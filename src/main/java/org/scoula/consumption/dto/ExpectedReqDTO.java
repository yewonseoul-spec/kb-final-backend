package org.scoula.consumption.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpectedReqDTO {
    // 예상 소비 등록/수정 요청 DTO
    private String expectedDate;

    private Long categoryNo;

    private Long expectedAmount;

    private String merchant;

    private String memo;

}
