package org.scoula.engine.dto;

import lombok.Data;
import java.util.Date;
import java.util.List;

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
    private String conflictGroupCode;
    private String isActive;
    private Date applyEndDate;
    private String plcyNo;              // 온통청년 정책 고유번호
    private String aplyUrlAddr;         // 신청 URL (없거나 형식이 깨진 값이 많아 화면에서 방어 필요)
    private Integer inqCnt;             // 조회수 (engine-05 인기도 점수)
    private int score;                  // engine-05: 추천 점수 (0~100)
    private List<String> scoreDetail;   // engine-05: 매칭 근거 (항목별 표시용)
}
