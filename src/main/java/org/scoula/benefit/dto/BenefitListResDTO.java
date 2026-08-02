package org.scoula.benefit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class BenefitListResDTO {
    private Integer benefitNo;
    private String plcyNo;
    private String plcyNm;

    private String categoryCode;
    private String categoryName;

    private String sprvsnInstCdNm;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate applyStartDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate applyEndDate;

    private String aplyPrdSeCd;
    private String isActive;

    private Boolean closed;
    private String benefitStatus;
}
