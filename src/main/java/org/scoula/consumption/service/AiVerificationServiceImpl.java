package org.scoula.consumption.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.scoula.admin.service.PromptService;
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

    // [상호 추가] AI 프롬프트 관리 화면(/admin/prompt)에서 이 프롬프트를 수정할 수 있게 하려고 넣었습니다.
    //            DB 조회가 실패하면 기존 AiVerificationPrompt 상수로 그대로 떨어지므로
    //            프롬프트 관리 기능이 죽어도 이 검증은 원래대로 동작합니다.
    private final PromptService promptService;

    // [상호 추가] 기존에는 생성자가 없었는데 PromptService 주입을 위해 만들었습니다.
    public AiVerificationServiceImpl(PromptService promptService) {
        this.promptService = promptService;
    }

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Override
    public AiVerdictDTO verify(String requestJson, String responseJson) {
        // [상호 수정] 본문을 아래 오버로드로 옮기고 여기서는 null 을 넘겨 호출만 합니다.
        //            null 이면 예전과 똑같이 DB 활성 버전을 읽으므로 동작은 변하지 않습니다.
        return verify(requestJson, responseJson, null);
    }

    /**
     * [상호 추가] 관리자 프롬프트 시험 실행용 오버로드.
     *
     * systemPromptOverride 에 값이 있으면 그것을 쓰고,
     * 없으면 기존과 같이 DB 활성 버전(없으면 코드 상수)을 씁니다.
     */
    @Override
    public AiVerdictDTO verify(String requestJson, String responseJson, String systemPromptOverride) {
        try {
            String userMessage = "원본 데이터: " + requestJson + "\n\n분석 결과: " + responseJson;

            // [상호 수정] AiVerificationPrompt.VERIFICATION_SYSTEM_PROMPT 를 직접 쓰던 것을
            //            DB 조회로 바꿨습니다. DB에 값이 없으면 기존 상수를 그대로 씁니다.
            String systemPrompt =
                    (systemPromptOverride != null && !systemPromptOverride.isBlank())
                            ? systemPromptOverride
                            : promptService.getOrDefault(
                            "CONSUMPTION_VERIFICATION",
                            AiVerificationPrompt.VERIFICATION_SYSTEM_PROMPT);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", 200,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
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