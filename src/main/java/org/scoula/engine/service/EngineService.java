package org.scoula.engine.service;

import org.scoula.engine.dto.BenefitResDto;
import org.scoula.engine.dto.EngineResultDto;

import java.util.List;

public interface EngineService {

        EngineResultDto findEligibleBenefits(int memberNo);
}
