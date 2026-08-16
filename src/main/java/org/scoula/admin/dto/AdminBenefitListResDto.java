package org.scoula.admin.dto;

import lombok.Data;

import java.util.Date;

// admin-02: 혜택 목록 한 행 (MyBatis resultType이라 setter 필요)
@Data
public class AdminBenefitListResDto {
    private Integer benefitNo;
    private String plcyNo;
    private String plcyNm;
    private String categoryCode;
    private String sprvsnInstCdNm;   // 주관기관. 동명 혜택 구분에 필요
    private Date applyEndDate;
    private Integer dday;            // 마감까지 남은 일수. 상시모집이면 null
    private String aplyPrdSeCd;
    private String isActive;
    private Integer inqCnt;
    private String conflictGroupCode;
    private Date frstRegDt;

    // 중복수혜 규칙 건수.
    // 그룹형(conflict_group_code)만 보이던 것을 개별쌍까지 확장한다.
    // 셋을 합치지 않고 나누는 이유는 성격이 다르기 때문이다.
    //   pairRuleCount     : 정책끼리의 관계. 엔진이 실제로 적용한다
    //   externalRuleCount : 외부 제도 안내. trigger_benefit_no가 NULL이고 점수에 영향이 없다
    //   reviewRuleCount   : 아직 검수되지 않아 엔진이 무시하는 규칙. 관리자가 처리할 대상
    private Integer pairRuleCount;
    private Integer externalRuleCount;
    private Integer reviewRuleCount;
    /** 관리자 지정 활성 상태. 지정이 없으면 null */
    private String adminIsActive;
    /** API 에서 사라진 정책인지 */
    private String apiDeletedYn;
    /** 최종 노출 상태. D=API삭제 / Y=활성 / N=비활성 */
    private String effectiveStatus;
}