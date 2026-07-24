package org.scoula.consumption.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpendingVO {

    private Long spendingNo;
    private Long memberNo;

    private Long categoryNo;
    private String categoryName;

    private Long amount;
    private String merchant;

    private LocalDate spendingDate;
}
