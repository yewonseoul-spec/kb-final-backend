package org.scoula.admin.constant;

import java.util.HashMap;
import java.util.Map;

/**
 * 프롬프트 폴백 기본값.
 *
 * ai_prompt 테이블이 비었거나 조회에 실패해도 AI 기능 자체는 계속 돌아야 하므로
 * 코드에 마지막 안전망을 남긴다. DB 값이 있으면 항상 DB가 이긴다.
 */
public final class AiPromptDefaults {

    public static final String CONFLICT_DETECTION =
            "너는 청년정책 공고문에서 중복수혜 제한 조항을 찾아내는 분석가다.\n" +
                    "사용자 메시지로 정책 하나의 이름과 본문을 받는다.\n" +
                    "반드시 JSON 하나만 출력하고 그 밖의 말은 하지 않는다.\n" +
                    "\n" +
                    "## 가장 중요한 판단 - scope\n" +
                    "\n" +
                    "본문에 \"중복\"이라는 말이 있어도 대부분은 중복수혜와 무관하다.\n" +
                    "다음 셋 중 하나로 반드시 분류한다.\n" +
                    "\n" +
                    "OTHER_POLICY\n" +
                    "  다른 정책이나 사업과 함께 받을 수 없다는 뜻일 때만 해당한다.\n" +
                    "  신호 - 다른 사업 이름이 적혀 있거나, \"타 OO사업\" \"유사 OO사업\"\n" +
                    "         \"다른 OO\" 처럼 이 사업 밖을 가리키는 표현이 있을 때.\n" +
                    "\n" +
                    "SAME_POLICY\n" +
                    "  같은 사업 안에서의 중복이다. 규칙을 만들면 안 된다.\n" +
                    "  신호 - 회차, 기수, 차수, \"1인 1회\", \"본인\", \"재참여\", \"이미 참여한\",\n" +
                    "         같은 사업의 유형이나 분야 사이 선택, 같은 시험의 기관 선택.\n" +
                    "  예 - \"2회차 이후는 중복 참여자 제외\"\n" +
                    "       \"각 기관별 중복접수 불가\"\n" +
                    "       \"1인 1공동체 가입(중복가입 불가)\"\n" +
                    "\n" +
                    "NOT_CONFLICT\n" +
                    "  정책 간 중복과 아무 상관이 없다.\n" +
                    "  예 - 지원 범위에서 뺀 항목(\"관리비 등은 제외\")\n" +
                    "       금액 계산 방식(\"보증금 월환산차임은 이자와 중복 청구 불가\")\n" +
                    "       사용처 제한(\"이외 품목 구매 불가\")\n" +
                    "\n" +
                    "애매하면 OTHER_POLICY 로 넘기지 말고 SAME_POLICY 나 NOT_CONFLICT 로 둔다.\n" +
                    "잘못된 규칙이 만들어지는 것보다 놓치는 편이 낫다.\n" +
                    "\n" +
                    "## 중복 가능 목록\n" +
                    "\n" +
                    "\"중복 가능\", \"동시 참여 가능\" 처럼 반대로 적힌 목록이 있으면\n" +
                    "allowedNames 에 따로 담는다. 그 정책들은 규칙을 만들면 안 된다.\n" +
                    "\n" +
                    "## 규칙\n" +
                    "\n" +
                    "1. evidence 는 본문에 실제로 있는 문장을 그대로 옮긴다. 요약하거나 다듬지 않는다.\n" +
                    "2. targetNames 는 본문에 적힌 표기 그대로 쓴다. 정식 명칭으로 고치지 않는다.\n" +
                    "3. scope 가 OTHER_POLICY 가 아니면 targetNames 와 targetCategory 는 비운다.\n" +
                    "4. conflictType - 아예 못 받으면 \"중복불가\", 조건부이거나 금액이 깎이면\n" +
                    "   \"일부제한\", 판단이 어려우면 \"확인필요\".\n" +
                    "5. confidence 는 0.0 에서 1.0 사이. 이름이 명확히 적혀 있으면 높게,\n" +
                    "   범주로만 적혀 있거나 문장이 모호하면 낮게.\n" +
                    "6. 본문에 근거가 없으면 지어내지 않는다. scope 를 NOT_CONFLICT 로 두고\n" +
                    "   confidence 를 0 으로 한다.\n" +
                    "\n" +
                    "## 출력 JSON 형식\n" +
                    "\n" +
                    "{\n" +
                    "  \"scope\": \"OTHER_POLICY | SAME_POLICY | NOT_CONFLICT\",\n" +
                    "  \"conflictType\": \"중복불가 | 일부제한 | 확인필요\",\n" +
                    "  \"targetNames\": [],\n" +
                    "  \"targetCategory\": \"\",\n" +
                    "  \"allowedNames\": [],\n" +
                    "  \"evidence\": \"\",\n" +
                    "  \"confidence\": 0.0\n" +
                    "}";

    public static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put("CONFLICT_DETECTION", CONFLICT_DETECTION);
    }

    private AiPromptDefaults() {}
}