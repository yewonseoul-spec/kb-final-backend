package org.scoula.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.scoula.admin.domain.ConflictCandidateVO;
import org.scoula.admin.dto.BenefitNameDto;
import org.scoula.admin.dto.ConflictAiItemDto;
import org.scoula.admin.dto.ConflictAiRunDto;
import org.scoula.admin.dto.ConflictAiSourceDto;
import org.scoula.admin.dto.ConflictAiStatsDto;
import org.scoula.admin.dto.ConflictRelationDto;
import org.scoula.admin.mapper.ConflictAiMapper;
import org.scoula.admin.mapper.ConflictCandidateMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConflictAiServiceImpl implements ConflictAiService {

    /**
     * "중복" 이 없는 후보를 잡는 보조 패턴.
     * "중복" 자체는 LIKE 로 처리하므로 여기에 넣지 않는다.
     */
    public static final String CONFLICT_PATTERN =
            "(타|다른|유사)[ ]*(사업|제도|지원사업|지원 사업|정책)|동시|병행";

    private static final String PROMPT_KEY  = "CONFLICT_DETECTION";
    private static final String OPENAI_URL  = "https://api.openai.com/v1/chat/completions";

    /** 본문이 길면 토큰이 급증한다. 잘린 뒤쪽 조항은 못 잡는 한계가 있다 */
    private static final int MAX_BODY_LENGTH = 6000;

    private final ConflictAiMapper conflictAiMapper;
    private final ConflictCandidateMapper conflictCandidateMapper;
    private final PromptService promptService;

    @Value("${openai.api-key}")
    private String apiKey;

    /** 프로퍼티가 없으면 gpt-4o-mini 로 떨어진다. 모델 교체는 프로퍼티로만 */
    @Value("${openai.conflict-model:gpt-4o-mini}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    // ------------------------------------------------------------
    // dry-run
    // ------------------------------------------------------------

    @Override
    public ConflictAiRunDto dryRun(int offset, int limit) {

        List<ConflictAiSourceDto> sources =
                conflictAiMapper.findConflictCandidates(CONFLICT_PATTERN);

        String systemPrompt = promptService.get(PROMPT_KEY);

        int from = Math.max(0, Math.min(offset, sources.size()));
        int to   = (limit <= 0) ? sources.size() : Math.min(sources.size(), from + limit);

        System.out.println("[중복분류] 전체 " + sources.size() + "건 중 "
                + from + " ~ " + to + " 구간 / 모델 " + model
                + " / 프롬프트 " + systemPrompt.length() + "자");

        List<ConflictAiItemDto> results = new ArrayList<>();
        ConflictAiStatsDto stats = new ConflictAiStatsDto();

        long startedAt = System.currentTimeMillis();
        int done = 0;

        for (int i = from; i < to; i++) {
            ConflictAiSourceDto s = sources.get(i);
            done++;

            String body = buildUserMessage(s);

            ConflictAiItemDto item = classifyOne(systemPrompt, body);
            item.setBenefitNo(s.getBenefitNo());
            item.setPlcyNm(s.getPlcyNm());
            item.setSprvsnInstCdNm(s.getSprvsnInstCdNm());
            item.setIsActive(s.getIsActive());
            item.setSourceTextHash(sha256(body));
            item.setModelName(model);
            item.setPromptVersion(promptService.getActiveVersion(PROMPT_KEY));

            results.add(item);
            collect(stats, item);

            System.out.println("[중복분류] " + (i + 1) + "/" + sources.size()
                    + " benefit_no=" + s.getBenefitNo()
                    + " scope=" + item.getScope()
                    + " rel=" + item.getRelations().size()
                    + (item.getErrorMsg() != null ? " ERR=" + item.getErrorMsg() : ""));
        }

        stats.setTotal(done);
        buildGateFunnel(stats, results);

        long ms = System.currentTimeMillis() - startedAt;
        System.out.println("[중복분류] 구간 완료 " + done + "건 / " + ms + "ms"
                + " / 다음 offset=" + to);

        ConflictAiRunDto run = new ConflictAiRunDto();
        run.setCandidateTotal(sources.size());
        run.setOffset(from);
        run.setNextOffset(to < sources.size() ? to : -1);
        run.setDurationMs(ms);
        run.setStats(stats);
        run.setItems(results);
        return run;
    }

    // ------------------------------------------------------------
    // 저장
    // ------------------------------------------------------------

    /**
     * 분석 결과를 Candidate 로 저장한다.
     *
     * 이 단계에서는 gate 판정을 하지 않는다.
     * 자연어 의미를 최대한 그대로 보존하는 것이 Candidate 의 역할이고,
     * 무엇을 실제 Rule 로 올릴지는 다음 단계 Java gate 가 정한다.
     */
    @Override
    public ConflictAiRunDto runAndSave(int offset, int limit) {

        ConflictAiRunDto run = dryRun(offset, limit);

        List<BenefitNameDto> dict = conflictAiMapper.findAllBenefitNames();
        ConflictResolver resolver = new ConflictResolver(dict);
        System.out.println("[중복분류] 정책명 사전 " + dict.size() + "건 로드");

        Integer promptVersion = promptService.getActiveVersion(PROMPT_KEY);
        int saved = 0;

        for (ConflictAiItemDto item : run.getItems()) {
            if (!"OTHER_POLICY".equals(item.getScope())) continue;
            saved += saveCandidates(item, resolver, promptVersion);
        }

        System.out.println("[중복분류] Candidate 저장 " + saved + "건");
        return run;
    }

    /**
     * 상대 정책을 직접 분석한다.
     *
     * Cross-check 는 상대 공고문이 우리를 되짚었는지 확인하는 단계인데,
     * 상대가 후보 필터에 걸리지 않으면 분석 자체가 안 되어 있어
     * "언급 없음" 과 "분석 안 함" 을 구분할 수 없었다.
     *
     * 실제로 결혼지원금 두 정책이 서로를 지목하는 관계인데
     * 한쪽 공고문에 '중복' 이라는 단어가 없어 후보에서 빠졌고,
     * 그 결과 양방향 관계가 단방향으로 남았다.
     *
     * 그래서 상대가 특정된 경우에는 필터를 무시하고 직접 분석한다.
     */
    @Override
    public int analyzeOne(int benefitNo) {

        ConflictAiSourceDto s = conflictAiMapper.findSourceByBenefitNo(benefitNo);
        if (s == null) return 0;

        String body = buildUserMessage(s);
        String hash = sha256(body);
        Integer promptVersion = promptService.getActiveVersion(PROMPT_KEY);

        // 같은 본문 · 같은 프롬프트로 이미 분석했으면 다시 부르지 않는다
        if (conflictCandidateMapper.countAnalyzed(benefitNo, hash, promptVersion) > 0) {
            return 0;
        }

        String systemPrompt = promptService.get(PROMPT_KEY);
        ConflictAiItemDto item = classifyOne(systemPrompt, body);
        item.setBenefitNo(s.getBenefitNo());
        item.setPlcyNm(s.getPlcyNm());
        item.setSprvsnInstCdNm(s.getSprvsnInstCdNm());
        item.setIsActive(s.getIsActive());
        item.setSourceTextHash(hash);
        item.setModelName(model);
        item.setPromptVersion(promptVersion);

        if (!"OTHER_POLICY".equals(item.getScope())) {
            System.out.println("[Cross-check] 상대 분석 " + benefitNo
                    + " scope=" + item.getScope() + " (관계 없음)");
            return 0;
        }

        List<BenefitNameDto> dict = conflictAiMapper.findAllBenefitNames();
        ConflictResolver resolver = new ConflictResolver(dict);
        int saved = saveCandidates(item, resolver, promptVersion);

        System.out.println("[Cross-check] 상대 분석 " + benefitNo
                + " scope=" + item.getScope() + " 저장 " + saved + "건");
        return saved;
    }

    /**
     * 분석 결과를 Candidate 로 저장한다.
     * runAndSave 와 analyzeOne 이 같은 로직을 쓰도록 분리했다.
     */
    private int saveCandidates(ConflictAiItemDto item,
                               ConflictResolver resolver,
                               Integer promptVersion) {
        int saved = 0;

        for (ConflictRelationDto rel : item.getRelations()) {

            ConflictCandidateVO vo = new ConflictCandidateVO();

            vo.setSourceBenefitNo(item.getBenefitNo());
            vo.setScope(item.getScope());
            vo.setRelation(rel.getRelation());

            // AI 가 targetName 에 범주 표현을 계속 넣는다.
            // 프롬프트로 두 번 막았는데도 안 지켜서 여기서 결정론적으로 옮긴다.
            String name = rel.getTargetName();
            String category = rel.getTargetCategory();
            if (!ConflictNormalizer.isRealPolicyName(name)) {
                if (ConflictNormalizer.isBlank(category)) category = name;
                name = null;
            }
            vo.setTargetNameRaw(name);
            vo.setTargetCategoryRaw(ConflictNormalizer.isBlank(category) ? null : category);

            vo.setDirection(rel.getDirection());
            vo.setTiming(rel.getTiming());
            vo.setSubjectScope(rel.getSubject());
            vo.setRestrictionStage(rel.getRestrictionStage());
            vo.setCombinationApplicability(rel.getCombinationApplicability());
            vo.setConditionType(ConflictNormalizer.isBlank(rel.getConditionType())
                    ? null : rel.getConditionType());
            vo.setConditionText(ConflictNormalizer.isBlank(rel.getConditionText())
                    ? null : rel.getConditionText());

            vo.setEvidenceText(rel.getEvidence());
            vo.setConfidence(rel.getConfidence());

            boolean verified = name != null
                    && ConflictNormalizer.evidenceContains(rel.getEvidence(), name);
            vo.setEvidenceVerified(verified ? "Y" : "N");

            // 이름이 있을 때만 DB 연결을 시도한다
            if (name != null) {
                ConflictResolver.Result rr = resolver.resolve(name, item.getBenefitNo());
                vo.setResolverResult(rr.getResolverResult());
                vo.setMappedBenefitNo(rr.getMappedBenefitNo());
                vo.setResolverCandidates(rr.getCandidates());
            } else {
                vo.setResolverResult("NO_MATCH");
            }

            vo.setVerifierVerdict("NOT_RUN");
            vo.setCrosscheckResult("NOT_RUN");
            vo.setAnalysisStatus("SUCCESS");
            vo.setWorkflowStatus("UNRESOLVED");
            vo.setEnforcementState("NONE");
            vo.setReviewReason(initialReviewReason(vo));

            vo.setModelName(item.getModelName());
            vo.setPromptKey(PROMPT_KEY);
            vo.setPromptVersion(promptVersion);
            vo.setSourceTextHash(item.getSourceTextHash());
            vo.setDedupeKey(ConflictNormalizer.buildDedupeKey(
                    vo.getSourceBenefitNo(), vo.getMappedBenefitNo(),
                    vo.getDirection(), vo.getTargetCategoryRaw(),
                    vo.getTiming(), vo.getSubjectScope()));

            try {
                if (conflictCandidateMapper.insertCandidate(vo) > 0) saved++;
            } catch (Exception e) {
                System.out.println("[중복분류] 저장 실패 benefit_no="
                        + vo.getSourceBenefitNo() + " / " + e.getMessage());
            }
        }
        return saved;
    }

    /**
     * 왜 검수로 왔는지를 미리 적어둔다.
     * 관리자가 화면에서 "이건 왜 나한테 왔나" 를 묻지 않게 하려는 것이다.
     */
    private String initialReviewReason(ConflictCandidateVO vo) {
        if ("MULTI_MATCH".equals(vo.getResolverResult()))  return "MULTI_MATCH";
        if ("N".equals(vo.getEvidenceVerified()) && vo.getTargetNameRaw() != null)
            return "EXTRACTION_INVALID";
        if (vo.getTargetNameRaw() == null)                 return "NO_MATCH";
        if (!"BIDIRECTIONAL".equals(vo.getDirection()))    return "DIRECTION_UNKNOWN";
        if (!"YES".equals(vo.getCombinationApplicability()))
            return "COMBINATION_APPLICABILITY_UNKNOWN";
        if (vo.getConditionType() != null)                 return "CONDITIONAL";
        return null;
    }

    // ------------------------------------------------------------
    // 프롬프트 시험 실행
    // ------------------------------------------------------------

    /**
     * 활성 버전을 바꾸기 전에 결과를 눈으로 보기 위한 것이다.
     * 저장하지 않으므로 몇 번을 돌려도 Candidate 가 늘지 않는다.
     */
    @Override
    public ConflictAiItemDto testPrompt(int benefitNo, String promptContent) {

        ConflictAiSourceDto s = conflictAiMapper.findSourceByBenefitNo(benefitNo);
        if (s == null) {
            ConflictAiItemDto err = errorItem("정책번호를 찾을 수 없습니다: " + benefitNo);
            err.setBenefitNo(benefitNo);
            return err;
        }

        String prompt = (promptContent == null || promptContent.trim().isEmpty())
                ? promptService.get(PROMPT_KEY)
                : promptContent;

        String body = buildUserMessage(s);
        ConflictAiItemDto item = classifyOne(prompt, body);

        item.setBenefitNo(s.getBenefitNo());
        item.setPlcyNm(s.getPlcyNm());
        item.setSprvsnInstCdNm(s.getSprvsnInstCdNm());
        item.setIsActive(s.getIsActive());
        item.setModelName(model);
        return item;
    }

    // ------------------------------------------------------------
    // AI 호출
    // ------------------------------------------------------------

    /** 한 건 실패가 배치 전체를 되돌리면 안 되므로 예외를 삼키고 errorMsg 로 남긴다 */
    private ConflictAiItemDto classifyOne(String systemPrompt, String userMessage) {
        try {
            String responseBody = callOpenAi(systemPrompt, userMessage);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");

            if (contentNode.isMissingNode() || contentNode.asText().isEmpty()) {
                return errorItem("응답에 content 없음");
            }

            String content = contentNode.asText().trim()
                    .replace("```json", "").replace("```", "").trim();

            return objectMapper.readValue(content, ConflictAiItemDto.class);

        } catch (Exception e) {
            return errorItem(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private ConflictAiItemDto errorItem(String msg) {
        ConflictAiItemDto item = new ConflictAiItemDto();
        item.setScope("ERROR");
        item.setErrorMsg(msg != null && msg.length() > 300 ? msg.substring(0, 300) : msg);
        return item;
    }

    private String callOpenAi(String systemPrompt, String userMessage) throws Exception {

        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("model", model);
        rootNode.put("temperature", 0);
        rootNode.put("max_tokens", 3000);
        rootNode.set("response_format",
                objectMapper.createObjectNode().put("type", "json_object"));

        ArrayNode messages = rootNode.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userMessage);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_URL))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        rootNode.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            String b = response.body();
            throw new IllegalStateException("OpenAI " + response.statusCode() + " : "
                    + b.substring(0, Math.min(300, b.length())));
        }
        return response.body();
    }

    private String buildUserMessage(ConflictAiSourceDto s) {
        StringBuilder sb = new StringBuilder();
        sb.append("정책명: ").append(nvl(s.getPlcyNm())).append("\n");
        sb.append("주관기관: ").append(nvl(s.getSprvsnInstCdNm())).append("\n\n");
        sb.append("[지원 내용]\n").append(nvl(s.getPlcySprtCn())).append("\n\n");
        sb.append("[신청 방법]\n").append(nvl(s.getPlcyAplyMthdCn())).append("\n\n");
        sb.append("[지원 대상]\n").append(nvl(s.getTargetDesc())).append("\n\n");
        sb.append("[소득 조건]\n").append(nvl(s.getEarnEtcCn()));

        String body = sb.toString();
        return body.length() > MAX_BODY_LENGTH ? body.substring(0, MAX_BODY_LENGTH) : body;
    }

    private String nvl(String v) {
        return v == null ? "" : v;
    }

    // ------------------------------------------------------------
    // 집계
    // ------------------------------------------------------------

    private void collect(ConflictAiStatsDto st, ConflictAiItemDto item) {

        if ("ERROR".equals(item.getScope())) {
            st.setFailed(st.getFailed() + 1);
            st.bump(st.getScope(), "ERROR");
            return;
        }

        st.bump(st.getScope(), item.getScope());

        for (ConflictRelationDto r : item.getRelations()) {
            st.setRelationCount(st.getRelationCount() + 1);

            st.bump(st.getRelation(), r.getRelation());
            st.bump(st.getDirection(), r.getDirection());
            st.bump(st.getTiming(), r.getTiming());
            st.bump(st.getSubject(), r.getSubject());
            st.bump(st.getRestrictionStage(), r.getRestrictionStage());
            st.bump(st.getCombinationApplicability(), r.getCombinationApplicability());
            st.bump(st.getConditionType(), r.getConditionType());

            boolean named = ConflictNormalizer.isRealPolicyName(r.getTargetName());
            if (named) {
                st.setNamedTargetCount(st.getNamedTargetCount() + 1);
                if (ConflictNormalizer.evidenceContains(r.getEvidence(), r.getTargetName())) {
                    st.setEvidenceVerified(st.getEvidenceVerified() + 1);
                } else {
                    st.setEvidenceFailed(st.getEvidenceFailed() + 1);
                }
            } else if (!ConflictNormalizer.isBlank(r.getTargetCategory())) {
                st.setCategoryOnlyCount(st.getCategoryOnlyCount() + 1);
                st.bump(st.getRawCategory(), r.getTargetCategory());
            } else if (!ConflictNormalizer.isBlank(r.getTargetName())) {
                // 이름 자리에 범주가 들어온 경우도 범주로 센다
                st.setCategoryOnlyCount(st.getCategoryOnlyCount() + 1);
                st.bump(st.getRawCategory(), r.getTargetName());
            }
        }
    }

    /**
     * gate 단계별 누적 통과 수.
     * 어느 줄에서 급락하는지가 곧 병목이다.
     */
    private void buildGateFunnel(ConflictAiStatsDto st, List<ConflictAiItemDto> items) {

        int otherPolicy = 0, forbidden = 0, named = 0, evidenceOk = 0,
                applicantCurrent = 0, noCondition = 0, bidirectional = 0, applicabilityYes = 0;

        for (ConflictAiItemDto item : items) {
            if (!"OTHER_POLICY".equals(item.getScope())) continue;
            otherPolicy++;

            for (ConflictRelationDto r : item.getRelations()) {
                if (!"FORBIDDEN".equals(r.getRelation())) continue;
                forbidden++;

                if (!ConflictNormalizer.isRealPolicyName(r.getTargetName())) continue;
                named++;

                if (!ConflictNormalizer.evidenceContains(r.getEvidence(), r.getTargetName())) continue;
                evidenceOk++;

                if (!"APPLICANT".equals(r.getSubject())) continue;
                if (!"CURRENT".equals(r.getTiming())) continue;
                applicantCurrent++;

                if (!ConflictNormalizer.isBlank(r.getConditionType())) continue;
                noCondition++;

                if ("BIDIRECTIONAL".equals(r.getDirection())) bidirectional++;
                if ("YES".equals(r.getCombinationApplicability())) applicabilityYes++;
            }
        }

        st.getGateFunnel().put("1_OTHER_POLICY",        otherPolicy);
        st.getGateFunnel().put("2_FORBIDDEN",           forbidden);
        st.getGateFunnel().put("3_NAMED_TARGET",        named);
        st.getGateFunnel().put("4_EVIDENCE_VERIFIED",   evidenceOk);
        st.getGateFunnel().put("5_APPLICANT_CURRENT",   applicantCurrent);
        st.getGateFunnel().put("6_NO_CONDITION",        noCondition);
        st.getGateFunnel().put("7_BIDIRECTIONAL",       bidirectional);
        st.getGateFunnel().put("7_APPLICABILITY_YES",   applicabilityYes);
    }

    private String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}