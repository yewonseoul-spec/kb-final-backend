package org.scoula.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.scoula.admin.constant.ConflictPromptKeys;
import org.scoula.admin.domain.AiPromptVO;
import org.scoula.admin.domain.ConflictAnalysisRunVO;
import org.scoula.admin.domain.ConflictCandidateVO;
import org.scoula.admin.domain.ConflictObservationVO;
import org.scoula.admin.dto.BenefitNameDto;
import org.scoula.admin.dto.ConflictAiItemDto;
import org.scoula.admin.dto.ConflictAiRunDto;
import org.scoula.admin.dto.ConflictAiSourceDto;
import org.scoula.admin.dto.ConflictAiStatsDto;
import org.scoula.admin.dto.ConflictRelationDto;
import org.scoula.admin.mapper.ConflictAiMapper;
import org.scoula.admin.mapper.ConflictAnalysisRunMapper;
import org.scoula.admin.mapper.ConflictCandidateMapper;
import org.scoula.admin.mapper.ConflictObservationMapper;
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

    private static final String PROMPT_KEY  = ConflictPromptKeys.CONFLICT_DETECTION;
    private static final String OPENAI_URL  = "https://api.openai.com/v1/chat/completions";

    /**
     * 한 실행에서 쓸 프롬프트 한 벌.
     *
     * 본문과 버전 번호를 따로 조회하면
     * 그 사이에 활성 버전이 바뀌었을 때 서로 다른 세대를 가리킬 수 있다.
     * 같은 행에서 한 번에 꺼내 함께 들고 다닌다.
     */
    private record PromptSnapshot(String content, Integer version) {}

    /** 본문이 길면 토큰이 급증한다. 잘린 뒤쪽 조항은 못 잡는 한계가 있다 */
    private static final int MAX_BODY_LENGTH = 6000;

    private final ConflictAiMapper conflictAiMapper;
    private final ConflictCandidateMapper conflictCandidateMapper;
    private final ConflictAnalysisRunMapper conflictAnalysisRunMapper;
    private final ConflictObservationMapper conflictObservationMapper;
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

    /**
     * 미리보기는 저장하지 않으므로 버전을 몰라도 돌아간다.
     * 프롬프트 관리 기능이 죽어도 결과는 볼 수 있어야 하므로
     * 코드 기본값으로 떨어지는 것을 그대로 허용한다.
     */
    @Override
    public ConflictAiRunDto dryRun(int offset, int limit) {
        return analyze(new PromptSnapshot(
                promptService.get(PROMPT_KEY),
                promptService.getActiveVersion(PROMPT_KEY)), offset, limit);
    }

    /**
     * 후보 구간을 분석한다. 저장은 하지 않는다.
     *
     * 어떤 프롬프트로 돌릴지는 호출하는 쪽이 정한다.
     * 저장하는 경로가 활성 버전을 다시 조회하면
     * 분석에 쓴 본문과 기록되는 버전이 어긋날 수 있기 때문이다.
     */
    private ConflictAiRunDto analyze(PromptSnapshot prompt, int offset, int limit) {

        List<ConflictAiSourceDto> sources =
                conflictAiMapper.findConflictCandidates(CONFLICT_PATTERN);

        String systemPrompt = prompt.content();

        int from = Math.max(0, Math.min(offset, sources.size()));
        int to   = (limit <= 0) ? sources.size() : Math.min(sources.size(), from + limit);

        System.out.println("[중복분류] 전체 " + sources.size() + "건 중 "
                + from + " ~ " + to + " 구간 / 모델 " + model
                + " / 프롬프트 v" + prompt.version()
                + " " + systemPrompt.length() + "자");

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
            item.setPromptVersion(prompt.version());

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
        // 저장하는 경로이므로 어느 세대인지 확실해야 한다
        return runAndSaveVersion(promptService.requireActiveVersion(PROMPT_KEY), offset, limit);
    }

    @Override
    public ConflictAiRunDto runAndSaveVersion(Integer version, int offset, int limit) {

        // 본문과 버전을 같은 행에서 함께 꺼낸다.
        // dryRun 을 재사용하면 그쪽이 활성 버전을 다시 조회하므로
        // 요청한 버전으로 기록하면서 다른 본문으로 분석하는 일이 생길 수 있다.
        AiPromptVO source = promptService.getVersion(PROMPT_KEY, version);
        PromptSnapshot prompt = new PromptSnapshot(source.getContent(), source.getVersion());

        ConflictAiRunDto run = analyze(prompt, offset, limit);

        List<BenefitNameDto> dict = conflictAiMapper.findAllBenefitNames();
        ConflictResolver resolver = new ConflictResolver(dict);
        System.out.println("[중복분류] 정책명 사전 " + dict.size() + "건 로드");

        Integer promptVersion = prompt.version();
        ConflictAiStatsDto stats = run.getStats();
        int saved = 0;

        for (ConflictAiItemDto item : run.getItems()) {
            // AI 가 말한 그대로를 따로 남긴다. 아래 저장 경로에는 관여하지 않는다.
            // 여기서 실패해도 후보 저장은 지금까지와 똑같이 진행된다.
            recordObservations(item, promptVersion, stats);

            if (!"OTHER_POLICY".equals(item.getScope())) continue;

            SaveOutcome outcome = saveCandidates(item, resolver, promptVersion);
            saved += outcome.inserted();

            stats.setCandidateAttempted(stats.getCandidateAttempted() + outcome.attempted());
            stats.setCandidateInserted(stats.getCandidateInserted() + outcome.inserted());
            stats.setCandidateIgnored(stats.getCandidateIgnored() + outcome.ignored());
            stats.setCandidateFailed(stats.getCandidateFailed() + outcome.failed());
        }

        System.out.println("[중복분류] Candidate 저장 " + saved + "건"
                + " (시도 " + stats.getCandidateAttempted()
                + " / 저장 " + stats.getCandidateInserted()
                + " / 무시 " + stats.getCandidateIgnored()
                + " / 실패 " + stats.getCandidateFailed() + ")");
        logShadowSummary(stats);
        return run;
    }

    /**
     * 후보는 저장하지 않고 관측만 남긴다.
     *
     * 저장 경로를 바꾸기 전에 기록 쪽이 실제로 도는지 먼저 봐야 하는데,
     * 기존 실행으로 확인하면 후보가 또 쌓인다.
     * 지금 고치려는 것이 바로 그 쌓임이므로 확인하는 행위가 문제를 늘리게 된다.
     *
     * dryRun 은 저장을 하지 않는다는 약속이 있어 여기에 얹지 않았다.
     * 그 약속을 깨면 미리보기를 눌렀을 때 무엇이 저장되는지 아무도 모르게 된다.
     */
    @Override
    public ConflictAiRunDto shadowRun(int offset, int limit) {

        Integer version = promptService.requireActiveVersion(PROMPT_KEY);
        AiPromptVO source = promptService.getVersion(PROMPT_KEY, version);
        PromptSnapshot prompt = new PromptSnapshot(source.getContent(), source.getVersion());

        ConflictAiRunDto run = analyze(prompt, offset, limit);
        ConflictAiStatsDto stats = run.getStats();

        for (ConflictAiItemDto item : run.getItems()) {
            recordObservations(item, prompt.version(), stats);
        }

        logShadowSummary(stats);
        return run;
    }

    /**
     * 병행 기록 결과를 남긴다.
     *
     * 실행 기록 실패와 관측 저장 실패를 나눠 적는다.
     * 실행을 못 만든 정책은 관측을 시도조차 하지 않으므로
     * 두 숫자를 합쳐 보면 무엇이 빠졌는지 알 수 없다.
     */
    private void logShadowSummary(ConflictAiStatsDto stats) {
        System.out.println("[병행기록] 실행 " + stats.getShadowRunsCreated()
                + "/" + stats.getShadowRunsAttempted()
                + " (실패 " + stats.getShadowRunsFailed()
                + ", 미완료 " + stats.getShadowRunsUnfinalized() + ")"
                + " / 관측 " + stats.getObservationsInserted()
                + "/" + stats.getObservationsAttempted()
                + " (실패 " + stats.getObservationsFailed() + ")");
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
        return analyzeOneVersion(benefitNo, promptService.requireActiveVersion(PROMPT_KEY));
    }

    @Override
    public int analyzeOneVersion(int benefitNo, Integer version) {

        ConflictAiSourceDto s = conflictAiMapper.findSourceByBenefitNo(benefitNo);
        if (s == null) return 0;

        AiPromptVO source = promptService.getVersion(PROMPT_KEY, version);
        Integer promptVersion = source.getVersion();

        String body = buildUserMessage(s);
        String hash = sha256(body);

        // 같은 본문 · 같은 프롬프트로 이미 분석했으면 다시 부르지 않는다
        if (conflictCandidateMapper.countAnalyzed(benefitNo, hash, promptVersion) > 0) {
            return 0;
        }

        ConflictAiItemDto item = classifyOne(source.getContent(), body);
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
        // 외부 계약(int 반환)은 그대로 두고 저장 성공 건수만 꺼낸다
        int saved = saveCandidates(item, resolver, promptVersion).inserted();

        System.out.println("[Cross-check] 상대 분석 " + benefitNo
                + " scope=" + item.getScope() + " 저장 " + saved + "건");
        return saved;
    }

    /**
     * Candidate 저장 시도 결과.
     *
     * INSERT IGNORE 는 UNIQUE 충돌에서 예외를 던지지 않고 0 을 돌려준다.
     * saved 하나만 세면 "AI 가 관계를 못 뽑은 것" 과
     * "뽑았지만 저장에서 사라진 것" 을 구분할 수 없어 나눠 센다.
     *
     * attempted = inserted + ignored + failed
     */
    private record SaveOutcome(int attempted, int inserted, int ignored, int failed) {}

    /**
     * 분석 결과를 Candidate 로 저장한다.
     * runAndSave 와 analyzeOne 이 같은 로직을 쓰도록 분리했다.
     */
    private SaveOutcome saveCandidates(ConflictAiItemDto item,
                                       ConflictResolver resolver,
                                       Integer promptVersion) {
        int attempted = 0, inserted = 0, ignored = 0, failed = 0;

        for (ConflictRelationDto rel : item.getRelations()) {

            ConflictCandidateVO vo = new ConflictCandidateVO();

            vo.setSourceBenefitNo(item.getBenefitNo());
            vo.setScope(item.getScope());
            vo.setRelation(rel.getRelation());

            // AI 가 targetName 에 범주 표현을 계속 넣는다.
            // 프롬프트로 두 번 막았는데도 안 지켜서 여기서 결정론적으로 옮긴다.
            // 같은 계산을 관측 기록 쪽에서도 하므로 한 곳에 두고 함께 쓴다.
            // 두 곳이 각자 계산하면 한쪽만 고쳤을 때 같은 AI 출력이 다른 키를 갖게 된다.
            ConflictNormalizer.TargetIdentity target = ConflictNormalizer.normalizeTarget(
                    rel.getTargetName(), rel.getTargetCategory());
            String name = target.name();
            String category = target.category();
            vo.setTargetNameRaw(name);
            vo.setTargetCategoryRaw(category);

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
                    vo.getSourceBenefitNo(), vo.getRelation(),
                    vo.getTargetNameRaw(), vo.getTargetCategoryRaw()));

            attempted++;
            try {
                if (conflictCandidateMapper.insertCandidate(vo) > 0) {
                    inserted++;
                } else {
                    // INSERT IGNORE 가 0 을 돌려준 경우다.
                    // 원인을 여기서 단정하지 않고 관측만 한다.
                    ignored++;
                    logIgnored(vo);
                }
            } catch (Exception e) {
                failed++;
                System.out.println("[중복분류] 저장 실패 benefit_no="
                        + vo.getSourceBenefitNo() + " / " + e.getMessage());
            }
        }
        return new SaveOutcome(attempted, inserted, ignored, failed);
    }

    /**
     * 저장되지 못한 시도를 남긴다.
     *
     * "중복이라 걸러졌다" 를 확정하는 로그가 아니라
     * "이 시도가 DB 에서 저장되지 않았다" 는 관측 기록이다.
     *
     * semantic 값을 함께 남기는 이유는,
     * 같은 의미인데 AI 출력이 흔들려 키가 갈렸는지를
     * 나중에 DB 의 기존 행과 대조해 판별하기 위해서다.
     */
    private void logIgnored(ConflictCandidateVO vo) {
        System.out.println("[중복분류] IGNORED"
                + " benefit_no=" + vo.getSourceBenefitNo()
                + " promptVersion=" + vo.getPromptVersion()
                + " hash=" + vo.getSourceTextHash()
                + " model=" + vo.getModelName()
                + " key=" + vo.getDedupeKey()
                + " relation=" + vo.getRelation()
                + " name=" + vo.getTargetNameRaw()
                + " category=" + vo.getTargetCategoryRaw()
                + " conditionType=" + vo.getConditionType()
                + " timing=" + vo.getTiming()
                + " subject=" + vo.getSubjectScope()
                + " stage=" + vo.getRestrictionStage()
                + " applicability=" + vo.getCombinationApplicability());
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
    public ConflictAiItemDto testPrompt(String plcyNo, String promptContent) {

        // [상호 수정] findSourceByBenefitNo → findSourceByPlcyNo
        //            benefit_no 는 재적재하면 재배정되므로 화면 입력을 plcy_no 로 바꿨다
        ConflictAiSourceDto s = conflictAiMapper.findSourceByPlcyNo(plcyNo);
        if (s == null) {
            // [상호 수정] err.setBenefitNo(benefitNo) 를 뺐다.
            //            plcyNo 가 문자열이라 int 필드에 넣을 수 없고,
            //            못 찾은 번호는 errorMsg 에 그대로 들어가므로 정보는 남는다
            return errorItem("정책번호를 찾을 수 없습니다: " + plcyNo);
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

    // ------------------------------------------------------------
    // 병행 기록
    // ------------------------------------------------------------

    /**
     * AI 가 반환한 관계를 원본 그대로 따로 기록한다.
     *
     * 기존 저장 경로는 그대로 두고 옆에 한 벌 더 남기는 것이다.
     * 지금 저장 경로는 같은 의미가 두 번 들어오면 먼저 온 줄만 남기는데,
     * 그러면 AI 가 한 응답 안에서 흔들렸다는 사실이 사라진다.
     * 그 사실을 보려면 걸러지기 전 상태가 어딘가 남아 있어야 한다.
     *
     * 여기서 실패해도 후보 저장은 지금까지와 똑같이 진행된다.
     * 관측 기록은 아직 아무도 읽지 않으므로,
     * 이것 때문에 기존 동작이 바뀌면 얻는 것 없이 위험만 늘어난다.
     *
     * 다만 실패를 조용히 넘기지는 않는다.
     * 예외를 삼켜 화면에는 성공으로 보이고 실제로는 저장이 안 된 적이 있었다.
     *
     * 실행 기록을 만들지 못하면 관측을 붙일 곳이 없으므로 그대로 끝낸다.
     * 실행 번호 없이 관측을 넣으려 하면 전건이 실패하고 로그만 늘어난다.
     */
    private void recordObservations(ConflictAiItemDto item, Integer promptVersion,
                                    ConflictAiStatsDto stats) {

        // 실행 번호를 try 밖에 둔다. 아래 catch 에서도 이 값이 필요하기 때문이다.
        // 안에서 선언하면 마지막 상태 기록이 실패했을 때
        // 어느 실행이 시작 상태로 남았는지 로그에 적을 수 없다.
        Integer runNo = null;

        try {
            stats.setShadowRunsAttempted(stats.getShadowRunsAttempted() + 1);

            runNo = openShadowRun(item, promptVersion, stats);
            if (runNo == null) return;

            // AI 호출이나 응답 해석이 실패한 건이다.
            // 관계가 없는 것과 읽지 못한 것은 다르므로 완료로 적지 않는다.
            if ("ERROR".equals(item.getScope())) {
                conflictAnalysisRunMapper.markExtractionFailed(runNo, item.getErrorMsg());
                return;
            }

            List<ConflictRelationDto> relations = item.getRelations();
            int attempted = 0, inserted = 0, failed = 0;

            if (relations != null) {
                for (int index = 0; index < relations.size(); index++) {
                    ConflictRelationDto rel = relations.get(index);
                    attempted++;
                    try {
                        conflictObservationMapper.insertObservation(
                                toObservation(item, rel, index, runNo));
                        inserted++;
                    } catch (Exception e) {
                        failed++;
                        logShadowFailure("관측 저장", item, promptVersion, runNo, index, rel, e);
                    }
                }
            }

            stats.setObservationsAttempted(stats.getObservationsAttempted() + attempted);
            stats.setObservationsInserted(stats.getObservationsInserted() + inserted);
            stats.setObservationsFailed(stats.getObservationsFailed() + failed);

            // 관계가 하나도 없는 응답도 여기로 온다.
            // 다만 이것을 "이 정책은 다시 분석하지 않아도 된다" 로 읽으면 안 된다.
            // 관계 없음인지 판단 불가인지는 아직 어디에도 적히지 않는다.
            if (failed == 0) {
                conflictAnalysisRunMapper.markObservationsReady(runNo, attempted, inserted);
            } else {
                conflictAnalysisRunMapper.markPartial(runNo, attempted, inserted, failed,
                        "관측 " + failed + "건 저장 실패");
            }

        } catch (Exception e) {
            // 여기까지 온 예외는 기존 저장 경로와 무관해야 한다.
            //
            // 실행 번호가 있다는 것은 기록은 만들어졌는데
            // 마지막 상태를 남기지 못했다는 뜻이다. 그 사실이 응답에 보여야 한다.
            // 로그에만 남기면 화면에는 모든 숫자가 성공으로 보인다.
            //
            // 실행을 만들지 못한 경우는 openShadowRun 이 이미 세고 돌아갔으므로
            // 여기서 다시 세면 같은 실패를 두 번 세게 된다.
            if (runNo != null) {
                stats.setShadowRunsUnfinalized(stats.getShadowRunsUnfinalized() + 1);
            }
            logShadowFailure("병행 기록", item, promptVersion, runNo, -1, null, e);
        }
    }

    /**
     * 실행 기록을 연다. 실패하면 비운 값을 돌려준다.
     *
     * 병행 기록은 정리 대상이 되면 안 되므로 일반 실행과 다른 SQL 을 쓴다.
     * 상태를 인자로 받게 만들면 언젠가 잘못 넘기게 된다.
     */
    private Integer openShadowRun(ConflictAiItemDto item, Integer promptVersion,
                                  ConflictAiStatsDto stats) {

        ConflictAnalysisRunVO run = new ConflictAnalysisRunVO();
        run.setSourceBenefitNo(item.getBenefitNo());
        run.setPromptKey(PROMPT_KEY);
        run.setPromptVersion(promptVersion);
        run.setSourceTextHash(item.getSourceTextHash());
        run.setModelName(item.getModelName());

        try {
            conflictAnalysisRunMapper.insertShadowRun(run);
        } catch (Exception e) {
            stats.setShadowRunsFailed(stats.getShadowRunsFailed() + 1);
            logShadowFailure("실행 기록", item, promptVersion, null, -1, null, e);
            return null;
        }

        if (run.getRunNo() == null) {
            stats.setShadowRunsFailed(stats.getShadowRunsFailed() + 1);
            System.out.println("[병행기록] 실행 번호를 받지 못했습니다"
                    + " benefit_no=" + item.getBenefitNo()
                    + " promptVersion=" + promptVersion);
            return null;
        }

        stats.setShadowRunsCreated(stats.getShadowRunsCreated() + 1);
        return run.getRunNo();
    }

    /**
     * AI 가 말한 그대로를 관측으로 옮긴다.
     *
     * 상대 정책 번호와 판정 결과는 담지 않는다.
     * 그것들은 공고문에 적힌 내용이 아니라 그 뒤 단계에서 우리가 만든 값이다.
     *
     * 이름과 범주를 가르는 계산은 후보 저장과 같은 함수를 쓴다.
     * 두 곳이 각자 계산하면 같은 AI 출력이 서로 다른 키를 갖게 된다.
     */
    private ConflictObservationVO toObservation(ConflictAiItemDto item, ConflictRelationDto rel,
                                                int index, int runNo) {

        ConflictNormalizer.TargetIdentity target = ConflictNormalizer.normalizeTarget(
                rel.getTargetName(), rel.getTargetCategory());

        ConflictObservationVO o = new ConflictObservationVO();

        o.setRunNo(runNo);
        o.setObservationIndex(index);
        o.setSourceBenefitNo(item.getBenefitNo());
        o.setScope(item.getScope());
        o.setRelation(rel.getRelation());

        o.setTargetNameRaw(target.name());
        o.setTargetCategoryRaw(target.category());

        o.setDirection(rel.getDirection());
        o.setTiming(rel.getTiming());
        o.setSubjectScope(rel.getSubject());
        o.setRestrictionStage(rel.getRestrictionStage());
        o.setCombinationApplicability(rel.getCombinationApplicability());

        o.setConditionType(ConflictNormalizer.isBlank(rel.getConditionType())
                ? null : rel.getConditionType());
        o.setConditionText(ConflictNormalizer.isBlank(rel.getConditionText())
                ? null : rel.getConditionText());

        o.setEvidenceText(rel.getEvidence());
        o.setEvidenceVerified(target.name() != null
                && ConflictNormalizer.evidenceContains(rel.getEvidence(), target.name())
                ? "Y" : "N");
        o.setConfidence(rel.getConfidence());

        o.setDedupeKey(ConflictNormalizer.buildDedupeKey(
                item.getBenefitNo(), rel.getRelation(), target.name(), target.category()));

        return o;
    }

    /**
     * 병행 기록 실패를 남긴다.
     *
     * 무엇이 어디서 실패했는지 알 수 있을 만큼만 적는다.
     * 공고문 본문이나 근거 문장 전체는 남기지 않는다.
     */
    private void logShadowFailure(String stage, ConflictAiItemDto item, Integer promptVersion,
                                  Integer runNo, int index, ConflictRelationDto rel, Exception e) {

        StringBuilder sb = new StringBuilder("[병행기록] ").append(stage).append(" 실패")
                .append(" benefit_no=").append(item == null ? null : item.getBenefitNo())
                .append(" promptVersion=").append(promptVersion)
                .append(" hash=").append(item == null ? null : item.getSourceTextHash())
                .append(" runNo=").append(runNo);

        if (index >= 0) sb.append(" index=").append(index);

        if (rel != null) {
            ConflictNormalizer.TargetIdentity target = ConflictNormalizer.normalizeTarget(
                    rel.getTargetName(), rel.getTargetCategory());
            sb.append(" relation=").append(rel.getRelation())
              .append(" name=").append(target.name())
              .append(" category=").append(target.category())
              .append(" key=").append(ConflictNormalizer.buildDedupeKey(
                      item == null ? null : item.getBenefitNo(),
                      rel.getRelation(), target.name(), target.category()));
        }

        sb.append(" / ").append(e.getClass().getSimpleName())
          .append(": ").append(e.getMessage());

        System.out.println(sb);
    }
}