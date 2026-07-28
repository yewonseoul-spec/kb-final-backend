package org.scoula.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

//engine-03·04·05·06
@Data
@AllArgsConstructor
public class EngineResultDto {
    private String status;                      // OK / PROFILE_REQUIRED
    private List<String> missingFields;         // 누락된 프로필 항목
    private List<BenefitResDto> benefits;       // 필터링된 정책 목록
    private List<BenefitResDto> topBenefits;    // 점수 상위 K개 (engine-05)
    private List<CombinationResDto> recommendedCombinations; // 추천 조합 최대 3개 (engine-06)
    private List<ConflictWarningDto> warnings;  // 경고 목록
    private List<ConflictWarningDto> externalWarnings; //외부 제도 충돌 경고

    /** 프로필이 없거나 필수 항목이 비어 추천을 진행할 수 없는 경우 */
    public static EngineResultDto profileRequired(List<String> missingFields) {
        return new EngineResultDto(
                "PROFILE_REQUIRED", missingFields,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
