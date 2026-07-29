package org.scoula.benefit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenefitCategoryResDTO {
    private String categoryCode;
    private String categoryName;
    private Integer displayOrder;
}
