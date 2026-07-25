package org.scoula.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;


//engine-03
@Data
@AllArgsConstructor
public class ConflictWarningDto {
    private int benefitNo;       // 경고 대상 정책 번호
    private String plcyNm;       // 정책명
    private String conflictType; // 일부제한 or 확인필요
    private String ruleText;     // 경고 문구
}
