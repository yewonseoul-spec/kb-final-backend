package org.scoula.admin.dto;

import lombok.Data;

import java.util.Date;

// admin-02: 혜택 상세. 목록에 없는 본문 항목까지 포함한다.
@Data
public class AdminBenefitDetailResDto {
    private Integer benefitNo;
    private String plcyNo;
    private String plcyNm;
    private String categoryCode;
    private String sprvsnInstCdNm;

    private String targetDesc;       // 지원 대상
    private String plcySprtCn;       // 지원 내용
    private String plcyAplyMthdCn;   // 신청 방법
    private String sbmsnDcmntCn;     // 제출 서류
    private String plcyExplnCn;      // 혜택 설명

    // 신청 URL은 둘을 나란히 들고 있는다.
    //   aplyUrlAddr    : 온통청년 원본. 동기화 때마다 덮인다
    //   customApplyUrl : 관리자 지정. 동기화 대상이 아니라 보존된다
    // 사용자에게는 지정값이 있으면 그것만 나가고, 원본은 이 관리 화면에서만 보인다.
    private String aplyUrlAddr;
    private String customApplyUrl;

    private Date applyStartDate;
    private Date applyEndDate;
    private String aplyYmd;
    private String aplyPrdSeCd;

    private Integer sprtTrgtMinAge;
    private Integer sprtTrgtMaxAge;
    private String earnCndSeCd;
    private Integer earnMinAmt;
    private Integer earnMaxAmt;
    private String earnEtcCn;        // 기타 소득조건 원문
    private String mrgSttsCd;

    private String conflictGroupCode;
    private Integer inqCnt;
    private String isActive;
    private Date frstRegDt;
    private Date lastMdfcnDt;

    private Integer regionCount;     // 매핑 건수. 전국 코드 오부여 판단에 쓴다
}