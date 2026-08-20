package org.scoula.admin.dto;

import lombok.Data;

@Data
public class ConflictAiSourceDto {
    private Integer benefitNo;
    private String  plcyNm;
    private String  sprvsnInstCdNm;
    private String  plcySprtCn;
    private String  plcyAplyMthdCn;
    private String  targetDesc;
    private String  earnEtcCn;
    private String  isActive;
}