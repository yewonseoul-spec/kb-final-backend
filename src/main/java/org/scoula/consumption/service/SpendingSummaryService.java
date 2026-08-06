package org.scoula.consumption.service;

public interface SpendingSummaryService {

    // 이번 달과 지난 달 소비 내역을 비교해서 카테고리별 지출/추이가 담긴 요약을 만든다
    String buildSummaryText(Integer memberNo, String yearMonth);
}
