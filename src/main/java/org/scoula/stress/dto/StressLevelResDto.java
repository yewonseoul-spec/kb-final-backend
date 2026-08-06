package org.scoula.stress.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// 충격 강도 한 단계. 화면에서 '+20%' 또는 '+50만원'으로 보여줄 문구까지 함께 내려준다.
@Data
@Builder
public class StressLevelResDto {
    private Integer scenarioNo;     // 계산 요청 시 이 번호를 보낸다
    private String shockLevel;      // LOW / MID / HIGH
    private String label;           // 낮음 / 보통 / 높음
    private BigDecimal changeRate;
    private Long fixedAmount;
    private String displayText;     // '식비 +20%' / '의료·건강 +50만원'
}
