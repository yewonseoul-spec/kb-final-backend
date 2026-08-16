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

    // 신청 URL은 세 값을 나란히 들고 있는다. 우선순위는 위에서부터다.
    //   customApplyUrl : 관리자 지정. 동기화 대상이 아니라 보존된다
    //   aplyUrlAddr    : 온통청년 원본 신청 주소. 동기화 때마다 덮인다
    //   refUrlAddr1    : 참고 주소. 신청 주소가 없을 때 공고 확인용으로 쓴다
    // 사용자에게는 이 순서로 하나만 나가고, 관리 화면에서만 셋을 다 보여준다.
    // 셋이 모두 비어 있으면 관리자가 직접 지정해야 하는 정책이다.
    private String customApplyUrl;
    private String aplyUrlAddr;
    private String refUrlAddr1;

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

    // 활성 상태도 같은 구조다. 우선순위는 위에서부터다.
    //   apiDeletedYn  : 오픈 API에서 사라져 숨김 처리된 정책인지
    //   adminIsActive : 관리자 지정. 동기화 대상이 아니라 보존된다
    //   isActive      : 온통청년 원본. 동기화 때마다 덮인다
    private String isActive;
    private String adminIsActive;
    private String apiDeletedYn;
    private Date apiDeletedDt;

    /** 최종 노출 상태. D=API삭제 / Y=활성 / N=비활성 */
    private String effectiveStatus;

    private Date frstRegDt;
    private Date lastMdfcnDt;

    private Integer regionCount;     // 매핑 건수. 전국 코드 오부여 판단에 쓴다
}