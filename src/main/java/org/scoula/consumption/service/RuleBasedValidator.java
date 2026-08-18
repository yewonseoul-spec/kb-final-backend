package org.scoula.consumption.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleBasedValidator {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private RuleBasedValidator() {
    }

    public static ValidationResult validate(String originalJson, String resultJson) {
        List<String> violations = new ArrayList<>();

        try {
            JsonNode original = objectMapper.readTree(originalJson);
            JsonNode result = objectMapper.readTree(resultJson);

            String summaryText = result.path("summaryText").asText();
            JsonNode insights = result.path("insights");

            // insights의 title, description을 전부 합쳐서 카테고리/숫자 검색에 사용
            StringBuilder insightsAllText = new StringBuilder();
            for (JsonNode insight : insights) {
                insightsAllText.append(insight.path("title").asText(""))
                        .append(" ")
                        .append(insight.path("description").asText(""))
                        .append(" ");
            }
            String insightsText = insightsAllText.toString();
            String fullText = summaryText + " " + insightsText;

            // 1. 총 지출 금액/증감액/방향 검증
            long thisMonthTotal = original.path("이번달총지출").asLong();
            long lastMonthTotal = original.path("지난달같은기간총지출").asLong();
            String overallDirection = original.path("전체지출방향").asText("");
            checkTotalSpend(summaryText, thisMonthTotal, lastMonthTotal, overallDirection, violations);

            // summaryText에는 %가 없어야 함 (규칙 18)
            if (summaryText.contains("%")) {
                violations.add("summaryText에 %가 포함되어 있습니다 (규칙 18 위반)");
            }

            // 2. 카테고리별 검증
            JsonNode categories = original.path("카테고리별지출");
            for (JsonNode category : categories) {
                checkCategory(category, insights, violations);
            }

            // 3. 쉼표 표기 검증
            checkCommaFormat(fullText, violations);

            // 4. insight title 말투 검증 (규칙 16: ~다체 금지)
            for (JsonNode insight : insights) {
                checkTitleTone(insight.path("title").asText(""), violations);
            }

            // 5. insights 개수 검증 (정확히 3개)
            if (insights.size() != 3) {
                violations.add("insights는 정확히 3개여야 하는데 " + insights.size() + "개입니다");
            }

        } catch (Exception e) {
            violations.add("검증 중 JSON 파싱 오류: " + e.getMessage());
        }

        return new ValidationResult(violations);
    }

    // 총 지출 금액이 summaryText에 그대로 들어있는지, 증감액 계산이 맞는지, 방향이 맞는지 확인
    private static void checkTotalSpend(String summaryText, long thisMonth, long lastMonth,
                                        String direction, List<String> violations) {
        String thisMonthFormatted = format(thisMonth);
        long diff = Math.abs(thisMonth - lastMonth); // 규칙 20: 큰 값 - 작은 값
        String diffFormatted = format(diff);

        if (!summaryText.contains(thisMonthFormatted)) {
            violations.add("summaryText에 이번 달 총 지출 금액(" + thisMonthFormatted + "원)이 없습니다");
        }
        if (!summaryText.contains(diffFormatted)) {
            violations.add("summaryText에 지난달 같은 기간 대비 증감액(" + diffFormatted + "원)이 없습니다");
        }

        boolean saysIncrease = containsAny(summaryText, "늘었", "증가", "많이 썼", "많이 지출");
        boolean saysDecrease = containsAny(summaryText, "줄었", "감소", "절약", "적게 썼", "적게 지출");

        if ("증가".equals(direction) && saysDecrease && !saysIncrease) {
            violations.add("전체지출방향은 '증가'인데 summaryText는 감소로 서술했습니다");
        }
        if ("감소".equals(direction) && saysIncrease && !saysDecrease) {
            violations.add("전체지출방향은 '감소'인데 summaryText는 증가로 서술했습니다");
        }
    }

    // insights에서 언급된 카테고리에 한해, 방향/금액이 원본 데이터와 맞는지 확인
    private static void checkCategory(JsonNode category, JsonNode insightsArray, List<String> violations) {
        String name = category.path("카테고리").asText("");
        if (name.isEmpty()) return;

        // 이 카테고리를 실제로 언급한 insight만 찾는다
        StringBuilder relevantText = new StringBuilder();
        for (JsonNode insight : insightsArray) {
            String title = insight.path("title").asText("");
            String description = insight.path("description").asText("");
            if (title.contains(name) || description.contains(name)) {
                relevantText.append(title).append(" ").append(description).append(" ");
            }
        }

        String categoryText = relevantText.toString();
        if (categoryText.isEmpty()) return; // 이 카테고리를 언급한 insight가 없으면 검증 대상 아님

        String vsLastMonth = category.path("지난달같은기간대비").asText("");
        long diffAmount = Math.abs(category.path("지난달대비차이").asLong());
        String diffFormatted = format(diffAmount);

        if ("신규".equals(vsLastMonth) && categoryText.contains("지난달")) {
            violations.add("'" + name + "'는 신규 카테고리인데 '지난달' 비교 표현이 포함되어 있습니다");
        }
        if (!"신규".equals(vsLastMonth) && diffAmount > 0 && !categoryText.contains(diffFormatted)) {
            violations.add("'" + name + "'의 지난달 대비 차이 금액(" + diffFormatted + "원)이 insights에 없습니다");
        }

        boolean saysIncrease = containsAny(categoryText, "늘었", "증가", "새로 생겼", "커졌", "많이 썼", "생겼");
        boolean saysDecrease = containsAny(categoryText, "줄었", "감소", "절약", "적게 썼");

        if (("증가".equals(vsLastMonth) || "신규".equals(vsLastMonth)) && saysDecrease && !saysIncrease) {
            violations.add("'" + name + "'는 증가/신규인데 감소로 서술된 것 같습니다");
        }
        if ("감소".equals(vsLastMonth) && saysIncrease && !saysDecrease) {
            violations.add("'" + name + "'는 감소인데 증가로 서술된 것 같습니다");
        }
    }

    // 원 앞의 숫자(금액)에 천 단위 쉼표가 올바르게 표기되었는지 확인 (예: 1,000,00원은 잘못된 표기)
    private static void checkCommaFormat(String text, List<String> violations) {
        Matcher matcher = Pattern.compile("([0-9][0-9,]*)\\s*원").matcher(text);
        while (matcher.find()) {
            String numberPart = matcher.group(1);
            if (!isValidCommaFormat(numberPart)) {
                violations.add("금액 표기가 잘못되었습니다: \"" + numberPart + "원\"");
            }
        }
    }

    private static boolean isValidCommaFormat(String numberPart) {
        String[] groups = numberPart.split(",", -1);

        if (groups.length == 1) {
            return groups[0].length() <= 3; // 쉼표 없이 4자리 이상이면 잘못된 표기
        }
        if (groups[0].isEmpty() || groups[0].length() > 3) {
            return false;
        }
        for (int i = 1; i < groups.length; i++) {
            if (groups[i].length() != 3) {
                return false;
            }
        }
        return true;
    }

    // title이 반말체(~다)로 끝나면 실패 (규칙 16), ~ㅂ니다는 허용
    private static void checkTitleTone(String title, List<String> violations) {
        String trimmed = title.trim();
        String withoutEmoji = trimmed.replaceAll("[\\p{So}\\p{Cn}\\s]+$", "");

        if (withoutEmoji.isEmpty()) {
            return;
        }

        boolean endsWithDa = withoutEmoji.endsWith("다");
        boolean endsWithNida = withoutEmoji.endsWith("니다");

        if (endsWithDa && !endsWithNida) {
            violations.add("title이 반말체(~다)로 끝났습니다: \"" + title + "\"");
        }
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String format(long amount) {
        return String.format("%,d", amount);
    }

    // 검증 결과: 통과 여부 + 위반 사유 목록
    public static class ValidationResult {
        private final boolean passed;
        private final List<String> violations;

        public ValidationResult(List<String> violations) {
            this.violations = violations;
            this.passed = violations.isEmpty();
        }

        public boolean isPassed() {
            return passed;
        }

        public String getReason() {
            return passed ? "규칙 기반 검증 통과" : String.join(" / ", violations);
        }
    }
}
