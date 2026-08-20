package org.scoula.admin.service;

import org.scoula.admin.domain.ConflictCandidateVO;

/**
 * 사용자에게 보이는 문장은 AI 가 만들지 않는다.
 *
 * 원문이 "제한될 수 있음" 인데 AI 가 "함께 신청할 수 없습니다" 로 다듬으면
 * 원문보다 강한 말이 사용자에게 그대로 나간다.
 * 그래서 AI 는 사실만 뽑고 문장은 여기서 고정 틀로 만든다.
 *
 * 같은 이유로 이 템플릿도 단정형을 쓰지 않는다.
 * 공고문이 범주를 말하고 예시를 든 경우가 많아
 * 특정 정책과 확정적으로 못 받는다고 말하면 원문보다 세진다.
 */
public final class ConflictRuleTextBuilder {

    private ConflictRuleTextBuilder() {}

    public static String build(ConflictCandidateVO c) {

        if (c.getMappedBenefitNo() == null) {
            return buildUnmatchedText(c);
        }

        String name = c.getTargetNameRaw();

        if ("CONDITIONAL".equals(c.getRelation())) {
            return name + "과(와) 함께 지원받는 경우 지원 내용이 달라질 수 있습니다. "
                    + "신청 전 공고문을 확인해주세요.";
        }

        boolean past = "PAST".equals(c.getTiming())
                || "CURRENT_OR_PAST".equals(c.getTiming())
                || "HISTORY".equals(c.getRestrictionStage());

        if (past) {
            return name + "에 현재 참여 중이거나 과거 참여 이력이 있는 경우 "
                    + "신청이 제한될 수 있습니다. 신청 전 공고문을 확인해주세요.";
        }

        if ("HOUSEHOLD".equals(c.getSubjectScope())) {
            return "본인 또는 가구원이 " + name + "에 참여 중인 경우 "
                    + "신청이 제한될 수 있습니다. 신청 전 공고문을 확인해주세요.";
        }

        return name + "과(와) 중복 신청이 제한될 수 있습니다. "
                + "신청 전 공고문을 확인해주세요.";
    }

    /**
     * 우리 DB 에 상대 정책이 없거나 조합 제외가 확정되지 않은 경우.
     *
     * 이름이 있으면 그 이름을 알려주는 것이 사용자에게 훨씬 유용하다.
     * "유사한 사업" 으로 뭉뚱그리면 무엇을 확인해야 할지 알 수 없다.
     */
    private static String buildUnmatchedText(ConflictCandidateVO c) {

        String name = c.getTargetNameRaw();
        if (name != null && !name.trim().isEmpty()) {
            return name + "에 참여 중이거나 참여 이력이 있는 경우 신청이 제한될 수 있습니다. "
                    + "신청 전 공고문을 확인해주세요.";
        }

        String raw = c.getTargetCategoryRaw();
        String field = extractField(raw);

        if (field != null) {
            return "정부나 다른 지자체의 " + field + " 관련 사업에 참여 중이거나 "
                    + "참여 이력이 있는 경우 신청이 제한될 수 있습니다. "
                    + "신청 전 공고문을 확인해주세요.";
        }

        return "정부나 다른 지자체의 유사한 사업에 참여 중이거나 참여 이력이 있는 경우 "
                + "신청이 제한될 수 있습니다. 신청 전 공고문을 확인해주세요.";
    }

    /**
     * 범주 표현에서 사용자가 알아볼 수 있는 분야만 뽑는다.
     * 분야가 없는 표현은 null 을 돌려 일반 문장으로 보낸다.
     * 없는 정보를 지어내지 않기 위해서다.
     */
    private static String extractField(String raw) {
        if (raw == null) return null;
        String t = raw.replaceAll("\\s", "");

        if (t.contains("자산형성"))                        return "자산형성";
        if (t.contains("인력양성"))                        return "인력양성";
        if (t.contains("일자리"))                          return "일자리";
        if (t.contains("창업"))                            return "창업";
        if (t.contains("중개보수") || t.contains("이사비")) return "이사비·중개보수";
        if (t.contains("주거") || t.contains("월세")
                || t.contains("임차") || t.contains("전세")) return "주거";
        if (t.contains("장학") || t.contains("학자금"))     return "장학·학자금";
        if (t.contains("응시료") || t.contains("자격증"))   return "자격증 응시료";
        if (t.contains("면접"))                            return "면접";
        if (t.contains("구직") || t.contains("취업"))       return "구직·취업";
        if (t.contains("인턴"))                            return "인턴";
        if (t.contains("문화") || t.contains("예술"))       return "문화·예술";
        if (t.contains("공동체") || t.contains("동아리"))   return "공동체·동아리";
        if (t.contains("건강") || t.contains("의료"))       return "건강·의료";
        if (t.contains("교육") || t.contains("훈련"))       return "교육·훈련";
        return null;
    }

    /**
     * 관리자 판정과 방향성을 엔진이 쓰는 conflict_type 으로 옮긴다.
     *
     * 감점은 금액이 실제로 깎이는 경우에만 준다.
     * 과거 이력이나 가구원 조건은 해당 여부를 우리가 모르므로 감점 근거가 없다.
     */
    public static String toConflictType(ConflictCandidateVO c) {
        if (c.getMappedBenefitNo() == null) return "확인필요";

        if ("PARTIAL".equals(c.getConflictDecision())
                || "AMOUNT_ADJUSTMENT".equals(c.getConditionType())) {
            return "일부제한";
        }

        if ("CONFIRMED_BLOCK".equals(c.getEnforcementState())) return "중복불가";

        return "확인필요";
    }
}