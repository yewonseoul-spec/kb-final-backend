package org.scoula.benefit.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BenefitFilterReqDTO {
    private String keyword;
    private String categoryCode;
    private String zipCd;

    private String plcyMajorCd;
    private String schoolCd;
    private String jobCd;
}
