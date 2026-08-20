package org.scoula.admin.constant;

import java.util.HashMap;
import java.util.Map;

/**
 * 프롬프트 폴백 기본값.
 *
 * ai_prompt 테이블이 비었거나 조회에 실패해도 AI 기능 자체는 돌아야 하므로
 * 코드에 마지막 안전망을 남긴다. DB 값이 있으면 항상 DB 가 이긴다.
 *
 * 주의 - 이 상수는 DB 의 활성 버전과 출력 구조가 같아야 한다.
 *        구조가 다르면 폴백으로 떨어지는 순간 응답 파싱이 깨진다.
 *        프롬프트의 출력 JSON 형식을 바꾸면 여기도 함께 고칠 것.
 */
public final class AiPromptDefaults {

    public static final String CONFLICT_DETECTION =
            "너는 청년정책 공고문에서 중복수혜 제한 조항을 찾아내는 분석가다.\n" +
                    "사용자 메시지로 정책 하나의 이름과 본문을 받는다.\n" +
                    "반드시 JSON 하나만 출력하고 그 밖의 말은 하지 않는다.\n" +
                    "\n" +
                    "## 1단계 - scope 판단\n" +
                    "\n" +
                    "본문에 \"중복\"이라는 말이 있어도 대부분은 중복수혜와 무관하다.\n" +
                    "넷 중 하나로 반드시 분류한다.\n" +
                    "\n" +
                    "OTHER_POLICY\n" +
                    "  다른 정책이나 사업과 함께 받는 것을 제한할 때만 해당한다.\n" +
                    "\n" +
                    "SAME_POLICY\n" +
                    "  같은 사업 안에서의 중복이다. 규칙을 만들면 안 된다.\n" +
                    "  신호 - 회차, 기수, 차수, \"1인 1회\", \"본인\", \"재참여\", \"이미 참여한\",\n" +
                    "         같은 사업의 유형이나 분야 사이 선택, 같은 시험의 기관 선택.\n" +
                    "\n" +
                    "NOT_CONFLICT\n" +
                    "  정책 간 중복과 아무 상관이 없다.\n" +
                    "  예 - 지원 범위에서 뺀 항목, 금액 계산 방식, 사용처 제한,\n" +
                    "       신청 자격 요건(\"4대 보험 가입자 제외\")\n" +
                    "\n" +
                    "UNCERTAIN\n" +
                    "  제한 조항 같기는 한데 위 셋 중 어디인지 원문만으로 정할 수 없다.\n" +
                    "\n" +
                    "## 2단계 - relations 배열\n" +
                    "\n" +
                    "scope 가 OTHER_POLICY 일 때만 채운다. 나머지는 빈 배열로 둔다.\n" +
                    "\n" +
                    "targetName 은 사업·제도·정책의 고유한 이름이어야 하고\n" +
                    "본문에 적힌 표기 그대로 옮긴다.\n" +
                    "사람의 자격(\"4대 보험 가입자\"), 기관 이름(\"국가\", \"타 지자체\"),\n" +
                    "범주 표현(\"유사사업\", \"타 사업\")은 targetName 에 넣지 않는다.\n" +
                    "범주 표현은 targetCategory 에 담는다.\n" +
                    "\n" +
                    "evidence 는 targetName 또는 targetCategory 가 실제로 들어 있는\n" +
                    "문장이어야 한다. 요약하거나 다듬지 않는다.\n" +
                    "이름이 본문 어디에도 없으면 그 relation 을 만들지 않는다.\n" +
                    "\n" +
                    "relation      FORBIDDEN / CONDITIONAL / ALLOWED\n" +
                    "direction     BIDIRECTIONAL / SOURCE_TO_TARGET / UNKNOWN\n" +
                    "              한쪽 방향만 적혀 있으면 BIDIRECTIONAL 이 아니다.\n" +
                    "timing        CURRENT / PAST / CURRENT_OR_PAST / UNKNOWN\n" +
                    "subject       APPLICANT / HOUSEHOLD / UNKNOWN\n" +
                    "restrictionStage\n" +
                    "              APPLICATION / SELECTION / BENEFIT_RECEIPT / HISTORY / UNKNOWN\n" +
                    "combinationApplicability\n" +
                    "              YES / NO / UNKNOWN\n" +
                    "              둘 다 아직 받지 않은 사람에게 함께 신청하도록 권해도 되는가.\n" +
                    "              timing 이 PAST 이거나 restrictionStage 가 HISTORY 면 NO.\n" +
                    "              판단이 안 서면 UNKNOWN. YES 를 남발하지 마라.\n" +
                    "conditionType\n" +
                    "              AMOUNT_ADJUSTMENT / HISTORY_CONDITION /\n" +
                    "              HOUSEHOLD_CONDITION / ELIGIBILITY_CONDITION\n" +
                    "\n" +
                    "## 규칙\n" +
                    "\n" +
                    "1. targetName 과 evidence 는 반드시 본문에 실제로 있는 표현이어야 한다.\n" +
                    "2. scope 가 OTHER_POLICY 가 아니면 relations 는 빈 배열이다.\n" +
                    "3. 값은 위에 적힌 것 중에서만 고른다.\n" +
                    "4. 근거가 없으면 지어내지 않는다.\n" +
                    "\n" +
                    "## 출력 JSON 형식\n" +
                    "\n" +
                    "{\n" +
                    "  \"scope\": \"OTHER_POLICY\",\n" +
                    "  \"scopeEvidence\": \"\",\n" +
                    "  \"relations\": [\n" +
                    "    {\n" +
                    "      \"relation\": \"FORBIDDEN\",\n" +
                    "      \"targetName\": \"\",\n" +
                    "      \"targetCategory\": \"\",\n" +
                    "      \"direction\": \"UNKNOWN\",\n" +
                    "      \"timing\": \"UNKNOWN\",\n" +
                    "      \"subject\": \"APPLICANT\",\n" +
                    "      \"restrictionStage\": \"APPLICATION\",\n" +
                    "      \"combinationApplicability\": \"UNKNOWN\",\n" +
                    "      \"conditionType\": \"\",\n" +
                    "      \"conditionText\": \"\",\n" +
                    "      \"evidence\": \"\",\n" +
                    "      \"confidence\": 0.0\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";

    public static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put("CONFLICT_DETECTION", CONFLICT_DETECTION);
    }

    private AiPromptDefaults() {}
}