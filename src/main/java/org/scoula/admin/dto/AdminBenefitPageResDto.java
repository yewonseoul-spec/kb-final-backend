package org.scoula.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

// admin-02: 혜택 목록 응답
@Data
@Builder
public class AdminBenefitPageResDto {
    private int page;
    private int size;
    private int totalCount;
    private int totalPages;

    private List<AdminBenefitListResDto> benefits;
}