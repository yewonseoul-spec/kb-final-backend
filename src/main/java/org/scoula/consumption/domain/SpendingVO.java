package org.scoula.consumption.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpendingVO {

    private Long spendingNo;
    private Integer memberNo;

    private Long categoryNo;
    private String categoryName;

    private Long amount;
    private String merchant;

    private String payMethod;
    private String memo;

    private LocalDate spendingDate;
}
