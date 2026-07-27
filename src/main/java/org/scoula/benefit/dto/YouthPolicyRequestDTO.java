package org.scoula.benefit.dto;

import lombok.Data;

@Data
public class YouthPolicyRequestDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String rtnType = "json";

    // 상세 조건
    private String plcyNm;
    private String plcyKywdNm;
    private String lclsfNm;
    private String mclsfNm;
    private String zipCd;
    private String plcyNo;
}