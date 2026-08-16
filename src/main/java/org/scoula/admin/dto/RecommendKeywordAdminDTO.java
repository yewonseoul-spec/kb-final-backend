package org.scoula.admin.dto;

import lombok.Data;

@Data
public class RecommendKeywordAdminDTO {

    private Integer keywordCode;
    private String keywordName;
    private Integer displayOrder;
    private String isActive;
}

