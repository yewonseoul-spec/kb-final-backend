package org.scoula.consumption.service;

public interface AiAnalysisService {
    // 회원(memberNo)의 요청에 대해 summaryText(이번 달 소비 요약)을 보내고 분석 결과(JSON 문자열)을 받는다
    String analyze(Integer memberNo, String summaryText);
}
