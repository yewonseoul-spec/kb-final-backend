package org.scoula.consumption.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.scoula.consumption.dto.AiVerdictDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
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

    private final Map<Integer, CachedResult> cache = new HashMap<>();

    @Value("${openai.api-key}")
    private String apiKey;
    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    public AiAnalysisServiceImpl(AiVerificationService aiVerificationService) {
        this.aiVerificationService = aiVerificationService;
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

    // 실제로 OpenAi API를 호출해서 분석 결과를 받아온다
    private String callOpenAi(String summaryJson) {
        try {
            // system 프롬프트
            String systemPrompt = AiPrompt.SPENDING_ANALYSIS_SYSTEM_PROMPT;

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
