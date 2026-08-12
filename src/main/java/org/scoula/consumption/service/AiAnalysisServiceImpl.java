package org.scoula.consumption.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Integer, CachedResult> cache = new HashMap<>();

    @Value("${openai.api-key}")
    private String apiKey;
    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Override
    public String analyze(Integer memberNo, String summaryJson) {
        // 오늘 이미 이 회원 번호의 회원을 분석한 결과가 있으면 그대로 돌려준다
        CachedResult cached = cache.get(memberNo);
        if (cached != null && cached.date.equals(LocalDate.now())) {
            return cached.json;
        }

        // 결과가 없으면 OpenAI 호출
        String result = callOpenAi(summaryJson);

        // 오늘 날짜와 함께 캐시에 저장
        cache.put(memberNo, new CachedResult(LocalDate.now(), result));

        return result;
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

//            if (response.statusCode() != 200) {
//                throw new RuntimeException("OpenAI API 오류 (" + response.statusCode() + "): " + response.body());
//            }

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
