package org.scoula.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

//admin-01: 수동 동기화 실행 결과
@Data
@AllArgsConstructor
public class SyncResultResDto {
    private String resultStatus;   // S=성공, F=실패
    private String message;
    private int totalCnt;          // 처리한 정책 수
    private int insertCnt;         // 신규 등록
    private int updateCnt;         // 기존 수정
    private int durationMs;        // 소요 시간
    private String errorMsg;       // 실패 시 원인
}
