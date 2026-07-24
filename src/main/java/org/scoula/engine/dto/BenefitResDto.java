package org.scoula.engine.dto;

import lombok.Data;
import java.util.Date;

//engine-01
@Data
public class BenefitResDto {
    private int benefitNo;
    private String plcyNm;
    private String categoryCode;
    private Integer sprtTrgtMinAge;
    private Integer sprtTrgtMaxAge;
    private Integer earnMinAmt;
    private Integer earnMaxAmt;
    private String earnCndSeCd;
    private String earnEtcCn;
    private String mrgSttsCd;
    private String schoolCd;
    private String jobCd;
    private String conflictGroupCode;
    private String isActive;
    private Date applyEndDate;
}
