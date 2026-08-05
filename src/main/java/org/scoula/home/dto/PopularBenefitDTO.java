package org.scoula.home.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PopularBenefitDTO {
    private Integer benefitNo;
    private String plcyNm;
    private String categoryName;
    private String sprvsnInstCdNm;
    private Integer inqCnt;
}
