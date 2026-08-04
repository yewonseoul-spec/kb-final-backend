package org.scoula.home.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

// 홈이 한 번에 받는 요약.
// 자산·소비는 각 도메인이 준비되면 필드만 추가한다.
@Getter
@Setter
public class HomeSummaryDTO {
    private List<PopularBenefitDTO> popularBenefits;
}
