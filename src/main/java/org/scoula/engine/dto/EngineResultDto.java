package org.scoula.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

//engine-03
@Data
@AllArgsConstructor
public class EngineResultDto {
    private List<BenefitResDto> benefits;       // 필터링된 정책 목록
    private List<ConflictWarningDto> warnings;  // 경고 목록
}
