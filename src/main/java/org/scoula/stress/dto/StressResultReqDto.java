package org.scoula.stress.dto;

import lombok.Data;

// stress-02: 스트레스 테스트 계산 요청
@Data
public class StressResultReqDto {
    private Integer memberNo;
    private String scenarioCode;   // INFLATION / RENT / RATE / MEDICAL / COMPLEX
    private String shockLevel;     // LOW / MID / HIGH
}