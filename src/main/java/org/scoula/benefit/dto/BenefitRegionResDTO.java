package org.scoula.benefit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenefitRegionResDTO {

    private String zipCd;
    private String regionName;
    private String parentRegionCode;
    private Boolean hasChildren;
}