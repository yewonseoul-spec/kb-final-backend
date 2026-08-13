package org.scoula.benefit.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsumptionCategoryResDTO {
//소비추천용
    private Integer categoryNo;
    private String categoryName;
    private Long totalAmount;
}