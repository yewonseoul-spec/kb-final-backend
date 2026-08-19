package org.scoula.benefit.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// 동기화가 처리한 혜택 한 건
@Data
@AllArgsConstructor
public class SyncedBenefitDTO {
    private Integer benefitNo;
    private String actionType;       // I=신규 / U=갱신 / D=API 삭제로 비활성화
    private String changedSummary;   // 갱신 시 바뀐 내용. 신규거나 변경 없으면 null
}
