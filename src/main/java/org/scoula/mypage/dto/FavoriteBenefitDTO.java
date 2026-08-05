package org.scoula.mypage.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class FavoriteBenefitDTO {
    private Integer benefitNo;
    private String plcyNm;
    private String categoryName;

    // 상시 모집 혜택은 마감일이 없다. null 이 정상값
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
    private Date applyEndDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private Date savedAt;
}