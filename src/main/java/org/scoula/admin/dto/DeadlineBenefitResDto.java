package org.scoula.admin.dto;

import lombok.Data;
import java.util.Date;

// admin-01: 대시보드 마감 임박 정책 (MyBatis resultType이라 setter 필요)
@Data
public class DeadlineBenefitResDto {
    private Integer benefitNo;
    private String plcyNm;
    private String categoryCode;   // 화면에서 이름으로 변환 (1 일자리 ~ 5 참여·권리)
    private Date applyEndDate;
    private Integer dday;          // 남은 일수
}
