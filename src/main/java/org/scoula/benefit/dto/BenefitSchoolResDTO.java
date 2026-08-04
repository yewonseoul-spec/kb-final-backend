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
public class BenefitSchoolResDTO {

    private String schoolCd;
    private String codeName;
    private Integer displayOrder;
}