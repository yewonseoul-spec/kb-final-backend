package org.scoula.benefit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenefitProfileFilterResDTO {

    private Integer memberNo;

    private Integer age;

    private String zipCd;
    private String regionName;

    private String plcyMajorCd;
    private String majorName;

    private String schoolCd;
    private String schoolName;

    private String jobCd;
    private String jobName;

    private String mrgSttsCd;
    private String marriageName;
}