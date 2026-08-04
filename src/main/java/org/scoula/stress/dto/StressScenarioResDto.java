package org.scoula.stress.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

// 시나리오 하나 = 충격 강도 3단계 묶음
@Data
@Builder
public class StressScenarioResDto {
    private String scenarioCode;
    private String scenarioName;
    private String description;
    private String targetCategory;

    // 강도 전부가 수치 없이 비어 있으면 계산할 수 없다.
    // 목록에서 숨기지 않고 비활성으로 표시해 '왜 4개뿐이지'라는 의문을 없앤다.
    private boolean available;
    private String unavailableReason;

    private List<StressLevelResDto> levels;
}