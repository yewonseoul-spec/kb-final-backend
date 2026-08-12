package org.scoula.benefit.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BenefitFilterReqDTO {
    private String keyword;
    private String categoryCode;
    private String zipCd;

    private String plcyMajorCd;
    private String schoolCd;
    private String jobCd;
    private String mrgSttsCd;
    private Integer age;

    // 목표 기반 추천이 후보를 중분류로 좁힐 때 쓴다
    private List<String> detailCategoryCodes;

    // null 이면 제한 없음. 목표 기반 추천만 값을 넣는다
    private Integer limit;
}
