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
import java.util.List;
import java.util.Map;

@Service
public class AiVerificationServiceImpl implements AiVerificationService {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Override
    public AiVerdictDTO verify(String requestJson, String responseJson) {
        try {
            String userMessage = "원본 데이터: " + requestJson + "\n\n분석 결과: " + responseJson;

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", 200,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", AiVerificationPrompt.VERIFICATION_SYSTEM_PROMPT),
                            Map.of("role", "user", "content", userMessage)
                    )
            );

            String httpRequestJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(httpRequestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return AiVerdictDTO.builder()
                        .passed(true)
                        .reason("검증 AI 호출 실패로 건너뜀")
                        .build();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").get(0).path("message").path("content").asText();

            JsonNode verdictNode = objectMapper.readTree(content);

            return AiVerdictDTO.builder()
                    .passed(verdictNode.path("passed").asBoolean(true))
                    .reason(verdictNode.path("reason").asText(""))
                    .build();

        } catch (Exception e) {
            System.out.println("AI 검증 중 에러 발생: " + e.getMessage());
            return AiVerdictDTO.builder()
                    .passed(true)
                    .reason("검증 중 예외 발생: " + e.getMessage())
                    .build();
        }
    }
}
