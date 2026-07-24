package org.scoula.consumption.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpectedSpendingVO {
    private Long expectedNo;

    private Long memberNo;

    private Long categoryNo;
    private String categoryName;

    private Long expectedAmount;

    private String merchant;

    private LocalDate expectedDate;
}
