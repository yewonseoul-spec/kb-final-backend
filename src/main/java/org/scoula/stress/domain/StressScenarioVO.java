package org.scoula.stress.domain;

import lombok.Data;

import java.math.BigDecimal;

// stress_scenario 한 행 (시나리오 코드 + 충격 강도 조합)
@Data
public class StressScenarioVO {
    private Integer scenarioNo;
    private String scenarioCode;    // INFLATION / RENT / RATE / MEDICAL / COMPLEX
    private String scenarioName;
    private String description;
    private String shockLevel;      // LOW / MID / HIGH
    private String targetCategory;  // spending_category.category_name과 매칭. COMPLEX만 'ALL'
    private BigDecimal changeRate;  // 비율형 시나리오. 금액형이면 null
    private Long fixedAmount;       // 금액형 시나리오. 비율형이면 null
}