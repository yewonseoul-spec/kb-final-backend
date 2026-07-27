package org.scoula.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

//engine-03·04
@Data
@AllArgsConstructor
public class EngineResultDto {
    private List<BenefitResDto> benefits;       // 필터링된 정책 목록
    private List<BenefitResDto> topBenefits;    // 점수 상위 K개 (engine-05)
    private List<ConflictWarningDto> warnings;  // 경고 목록
    private List<ConflictWarningDto> externalWarnings; //외부 제도 충돌 경고
}
