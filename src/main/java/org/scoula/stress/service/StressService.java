package org.scoula.stress.service;

import org.scoula.stress.dto.StressResultReqDto;
import org.scoula.stress.dto.StressResultResDto;
import org.scoula.stress.dto.StressScenarioResDto;

import java.util.List;

public interface StressService {

    // stress-01: 스트레스 시나리오 목록
    List<StressScenarioResDto> findScenarios();

    // stress-02: 스트레스 테스트 계산
    StressResultResDto calculate(StressResultReqDto req);
}