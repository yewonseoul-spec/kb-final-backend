package org.scoula.benefit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenefitMajorResDTO {

    private String plcyMajorCd;

    private String codeName;
    private Integer displayOrder;
}