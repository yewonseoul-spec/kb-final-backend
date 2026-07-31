package org.scoula.engine.dto;

import lombok.Data;

//engine-06: 조합 내부 충돌 검사용(trigger,target 번호 모두 필요)
@Data
public class ConflictRuleDto {
    private Integer triggerBenefitNo;
    private Integer targetBenefitNo;
    private String conflictType; // 중복불가 / 일부제한 / 확인필요
    private String ruleText;
}
