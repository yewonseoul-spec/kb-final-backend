package org.scoula.admin.dto;

import lombok.Data;

// admin-02: 혜택 목록 검색 조건. 모든 조건은 선택이며 null이면 무시된다.
@Data
public class AdminBenefitSearchReqDto {
    private String keyword;         // 혜택명 부분 일치
    private String isActive;        // Y / N
    private String categoryCode;    // 1~5
    private Boolean deadlineSoon;   // true면 30일 이내 마감만
    private Boolean hasConflict;    // true면 중복수혜 관리 대상만

    // 정렬 기준. 화면에서 컬럼 헤더를 눌러 보낸다.
    //   plcyNm / sprvsnInstCdNm / deadline / inqCnt
    // 값을 SQL에 문자열로 이어붙이지 않고 매퍼에서 <choose>로 분기하므로,
    // 허용 목록에 없는 값이 들어오면 기본 정렬(최신 등록순)로 떨어진다.
    private String sort;
    private String order;           // asc / desc

    private Integer page;           // 1부터
    private Integer size;
    private Integer offset;         // 서비스에서 계산해 채운다
    /** 숨김 처리된 정책만 보기 */
    private Boolean deletedOnly;
    /** 관리자가 상태를 지정한 정책만 보기 */
    private Boolean adminManagedOnly;
}