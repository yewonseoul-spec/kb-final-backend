package org.scoula.consumption.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDTO { // 카테고리
    private Long categoryNo;
    private String categoryName;

    @Override
    public String toString() {
        return "CategoryDTO{" +
                "categoryNo=" + categoryNo +
                ", categoryName='" + categoryName + '\'' +
                '}';
    }
}
