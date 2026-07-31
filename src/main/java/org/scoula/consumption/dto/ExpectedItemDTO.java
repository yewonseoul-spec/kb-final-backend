package org.scoula.consumption.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpectedItemDTO { // 일별 예상 소비 1건
    private Long expectedNo;

    private String categoryName;

    private Long amount;

    private String merchant;

    private String memo;

}
