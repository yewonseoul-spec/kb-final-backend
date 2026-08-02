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

    private Integer page;           // 1부터
    private Integer size;
    private Integer offset;         // 서비스에서 계산해 채운다
}