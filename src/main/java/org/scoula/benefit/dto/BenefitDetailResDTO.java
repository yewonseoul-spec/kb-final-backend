package org.scoula.benefit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenefitDetailResDTO {

    private Integer benefitNo;
    private String plcyNo;
    private String plcyNm;

    private String categoryCode;
    private String categoryName;

    private String sprvsnInstCdNm;
    private String targetDesc;

    private String plcyExplnCn;
    private String plcySprtCn;
    private Integer supportAmount;

    private String plcyAplyMthdCn;
    private String sbmsnDcmntCn;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate applyStartDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate applyEndDate;

    private String aplyYmd;
    private String aplyPrdSeCd;
    private String aplyUrlAddr;
    private String refUrlAddr1;

    private Integer sprtTrgtMinAge;
    private Integer sprtTrgtMaxAge;

    private String earnCndSeCd;
    private Integer earnMinAmt;
    private Integer earnMaxAmt;
    private String earnEtcCn;

    private String mrgSttsCd;
    private Integer inqCnt;
    private String isActive;

    private String benefitStatus;

    private List<String> regionNames;
    private List<String> majorNames;
    private List<String> schoolNames;
    private List<String> jobNames;
}