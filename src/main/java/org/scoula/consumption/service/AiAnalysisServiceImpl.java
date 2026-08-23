package org.scoula.consumption.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.scoula.admin.service.PromptService;
import org.scoula.consumption.dto.AiVerdictDTO;
import org.scoula.consumption.dto.ConsumptionPromptTestDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiAnalysisServiceImpl implements AiAnalysisService {

    // 검증에 실패했을 때 최대 몇 번까지 다시 시도할지
    private static final int MAX_ATTEMPTS = 2;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AiVerificationService aiVerificationService;

    // [상호 추가] AI 프롬프트 관리 화면(/admin/prompt)에서 이 프롬프트를 수정할 수 있게 하려고 넣었습니다.
    //            프롬프트가 코드에 있으면 한 줄 고칠 때마다 빌드와 재배포가 필요해서
    //            DB 조회가 실패하면 기존 AiPrompt 상수로 그대로 떨어지므로 동작은 변하지 않습니다.
    private final PromptService promptService;

    private final Map<Integer, CachedResult> cache = new HashMap<>();

    @Value("${openai.api-key}")
    private String apiKey;
    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    // [상호 수정] 생성자에 PromptService 파라미터를 하나 추가했습니다. 나머지는 그대로입니다.
    public AiAnalysisServiceImpl(AiVerificationService aiVerificationService,
                                 PromptService promptService) {
        this.aiVerificationService = aiVerificationService;
        this.promptService = promptService;
    }

    @Override
    public String analyze(Integer memberNo, String summaryJson) {
        CachedResult cached = cache.get(memberNo);
        if (cached != null && cached.date.equals(LocalDate.now())) {
            return cached.json;
        }

        String result = null;
        boolean passed = false;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            result = callOpenAi(summaryJson);

            RuleBasedValidator.ValidationResult ruleResult = RuleBasedValidator.validate(summaryJson, result);
            if (!ruleResult.isPassed()) {
                System.out.println("⚠️ [규칙 기반 검증 실패 - " + attempt + "번째 시도] 회원 "
                        + memberNo + ": " + ruleResult.getReason());
                continue;
            }

            passed = true;

            // 2차 AI는 참고용 로그만 남긴다
            AiVerdictDTO verdict = aiVerificationService.verify(summaryJson, result);
            if (!verdict.isPassed()) {
                System.out.println("ℹ️ [참고: AI 품질 코멘트] 회원 " + memberNo + ": " + verdict.getReason());
            }

            break;
        }

        if (passed) {
            cache.put(memberNo, new CachedResult(LocalDate.now(), result));
            return result;
        }

        System.out.println("🚨 [분석 최종 실패] 회원 " + memberNo + " - 재시도 " + MAX_ATTEMPTS + "회 모두 규칙 기반 검증 실패");
        return "{\"summaryText\": \"분석 결과를 불러오는 데 문제가 있어요. 잠시 후 다시 시도해 주세요.\", \"insights\": []}";
    }

    /**
     * [상호 추가] 관리자 프롬프트 시험 실행.
     *
     * analyze() 와 흐름은 같지만 캐시를 쓰지 않고 시도 이력을 남깁니다.
     * 기존 analyze() 는 한 줄도 고치지 않았습니다.
     */
    @Override
    public ConsumptionPromptTestDTO testAnalyze(String summaryJson,
                                                String analysisPrompt,
                                                String verificationPrompt) {
        long startedAt = System.currentTimeMillis();
        List<ConsumptionPromptTestDTO.Attempt> attempts = new ArrayList<>();

        String finalContent = null;
        boolean passed = false;

        try {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                String content = callOpenAi(summaryJson, analysisPrompt);

                RuleBasedValidator.ValidationResult ruleResult =
                        RuleBasedValidator.validate(summaryJson, content);

                attempts.add(ConsumptionPromptTestDTO.Attempt.builder()
                        .no(attempt)
                        .content(content)
                        .rulePassed(ruleResult.isPassed())
                        .violations(splitViolations(ruleResult))
                        .build());

                if (ruleResult.isPassed()) {
                    passed = true;
                    finalContent = content;
                    break;
                }
            }
        } catch (Exception e) {
            return ConsumptionPromptTestDTO.builder()
                    .summaryJson(summaryJson)
                    .attempts(attempts)
                    .finallyPassed(false)
                    .durationMs(System.currentTimeMillis() - startedAt)
                    .errorMsg("분석 호출에 실패했습니다: " + e.getMessage())
                    .build();
        }

        // AI 검증은 규칙 검증을 통과한 경우에만 돌린다.
        // 실제 운영 흐름과 같게 맞추기 위해서다.
        boolean aiPassed = true;
        String aiReason = "규칙 검증을 통과하지 못해 실행하지 않았습니다.";

        if (passed) {
            AiVerdictDTO verdict =
                    aiVerificationService.verify(summaryJson, finalContent, verificationPrompt);
            aiPassed = verdict.isPassed();
            aiReason = verdict.getReason();
        }

        return ConsumptionPromptTestDTO.builder()
                .summaryJson(summaryJson)
                .attempts(attempts)
                .finallyPassed(passed)
                .finalContent(finalContent)
                .aiVerdictPassed(aiPassed)
                .aiVerdictReason(aiReason)
                .durationMs(System.currentTimeMillis() - startedAt)
                .build();
    }

    /**
     * RuleBasedValidator 는 위반 사유를 " / " 로 이어 한 문장으로 돌려준다.
     * 한 번에 대여섯 개가 나올 수 있어 그대로 두면 화면에서 읽을 수가 없다.
     */
    private List<String> splitViolations(RuleBasedValidator.ValidationResult result) {
        if (result.isPassed()) {
            return List.of();
        }
        return Arrays.stream(result.getReason().split(" / "))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // 실제로 OpenAi API를 호출해서 분석 결과를 받아온다
    private String callOpenAi(String summaryJson) {
        // [상호 수정] 본문을 아래 오버로드로 옮기고 여기서는 null 을 넘겨 호출만 합니다.
        //            null 이면 예전과 똑같이 DB 활성 버전을 읽으므로 동작은 변하지 않습니다.
        return callOpenAi(summaryJson, null);
    }

    /**
     * [상호 추가] 프롬프트를 지정해 호출하는 형태.
     * systemPromptOverride 가 null 이거나 비어 있으면 기존과 같이 DB 활성 버전을 씁니다.
     */
    private String callOpenAi(String summaryJson, String systemPromptOverride) {
        try {
            // system 프롬프트
            // [상호 수정] AiPrompt.SPENDING_ANALYSIS_SYSTEM_PROMPT 를 직접 쓰던 것을
            //            DB 조회로 바꿨습니다. 관리자 화면에서 프롬프트를 고칠 수 있게 하려는 것입니다.
            //            DB에 값이 없거나 조회가 실패하면 두 번째 인자인 기존 상수를 그대로 씁니다.
            //            즉 AiPrompt.java 는 지우지 않고 폴백으로 계속 남아 있습니다.
            String systemPrompt =
                    (systemPromptOverride != null && !systemPromptOverride.isBlank())
                            ? systemPromptOverride
                            : promptService.getOrDefault(
                            "CONSUMPTION_ANALYSIS", AiPrompt.SPENDING_ANALYSIS_SYSTEM_PROMPT);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", 500,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", summaryJson)
                    )
            );

            // Map을 실제로 보낼 수 있는 JSON 문자열로 바꾼다
            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            // 실제로 요청을 보내고 응답을 받는다
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("OpenAI API 오류 (" + response.statusCode() + "): " + response.body());
            }

            // 응답 문자열에서 AI가 답변한 글을 꺼낸다
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (Exception e) {
            // 네트워크 문제 등으로 실패하면 사용자에게 오류 메시지를 표시한다
            throw new RuntimeException("AI 분석을 불러오지 못했어요. 잠시 후 다시 시도해 주세요. ", e);
        }
    }

    private static class CachedResult {
        LocalDate date;
        String json;

        CachedResult(LocalDate date, String json) {
            this.date = date;
            this.json = json;
        }
    }
}