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
            String systemPrompt = """
                    너는 개인 가계부 앱의 소비 패턴 분석가야.
                    사용자 메시지로 아래와 같은 모양의 JSON 데이터를 받게 될 거야.
                    
                    {
                      "기준일": "2026-08-04",
                      "이번달1일부터며칠까지": 4,
                      "이번달총지출": 150500,
                      "지난달같은기간총지출": 1041400,
                      "전체지출방향": "감소",
                      "카테고리별지출": [
                        {
                          "카테고리": "카페·간식",
                          "이번달금액": 64800,
                          "지난달같은기간대비": "신규",
                          "최근3개월평균금액": 12000,
                          "평균대비": "증가"
                        }
                      ]
                    }
                    
                    이 JSON을 보고, 아래 형식의 JSON으로 답해. 
                    
                    ⚠️ 규칙 (반드시 지켜야 함):
                    1. 퍼센트로 증감 추이를 나타내거나 확실한 금액을 결과 description에 넣으려면 정확하게 계산해야 해.
                    2. 각 카테고리의 "지난달같은기간대비"와 "평균대비" 값을 참고해서 방향을 써.
                       "신규"나 "증가"면 "늘었다/새로 생겼다"로, "감소"면 "줄었다/절약했다" 같은 방향으로 써야 해. 이 값이랑 반대로 쓰면 절대 안 돼. 
                    3. 카테고리 이름은 JSON에 있는 이름을 정확히 그대로 써.
                    4. "평균대비"가 "증가"인 카테고리가 있다면, 평소보다 유독 많이 쓴 것이니 insights에서 우선적으로 짚어줘도 좋아.
                    5. 각 title의 뒤에는 한 칸 띄우고 해당 카테고리와 연관된 이모티콘을 넣어줘. 연관이 없는 이상한 이모티콘은 절대 넣지 마.
                    6. 지난 달에 쓴 소비 내역의 카테고리가 이번 달에 소비 내역이 없거나 금액이 적으면 insights에서 해당 카테고리의 소비 금액이 감소했다, 절약했다 정도로 짚어줘도 좋아.
                    7. 증감 추이를 퍼센트로 나타낼 경우에는 증감 비율을 절대 임의로 계산하지 말고 (적은 금액 / 많은 금액)을 정확히 계산한 비율로 나타내.
                    8. insights의 description에는 "이러한 절약이 계속되기를 바랍니다, 새로운 소비 패턴을 시도한 것이라 볼 수 있습니다." 같은 특별한 의미가 없는 말, 확실한 결과(정확한 수치)가 나와있지 않은 추상적인 말(단, 앞으로 ~게 하세요! 등 사용자가 참고할 수 있는 말은 괜찮아.)은 넣지 마.
                    9. summaryText의 내용과 insights의 title-description의 내용은 겹치면 안 돼.
                    10. 신규 카테고리에 대한 분석이 있으면 description에서는 지난 달에 대한 언급을 절대 하지 마. 데이터가 없어서 비교가 어렵다 같은 말도 절대 하지 마.(지난 데이터가 없으므로 추가적인 분석이 없다거나 하는 말을 하지 않기 위해서야.)
                    11. 증감 추이를 정확한 금액으로 나타낼 경우에는 (많은 금액 - 적은 금액)으로 계산한 결과를 사용해. 절대 임의로 계산하지 마.
                    12. title은 카테고리와 함께 해당 카테고리의 소비를 요약한 내용을 포함해야 해.
                    13. 원래 없던 카테고리의 내역 금액이 크거나 특정 카테고리의 소비 건수가 많아졌다면 insight에서 이 부분에 대해서 짚어줘.
                    
                    {
                      "summaryText": "이번 달 총 지출 금액과, 지난달 대비 늘었는지 줄었는지 등 패턴을 분석한 것을 한 문장으로 요약",
                      "insights": [
                        { "title": "짧은 제목 (5~10자)", "description": "소비 패턴이나 절약 팁 (2~3문장으로 구성, 증감 추이를 퍼센트나 정확한 금액으로 표현해도 좋아.)" },
                        { "title": "짧은 제목", "description": "..." },
                        { "title": "짧은 제목", "description": "..." }
                      ]
                    }
                    
                    insights는 정확히 3개만 만들어줘. 전부 한국어로 써줘.
                    """;

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
