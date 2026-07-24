package org.scoula.engine.service;

import org.scoula.engine.dto.BenefitResDto;
import java.util.List;

public interface EngineService {

        List<BenefitResDto> findEligibleBenefits(int memberNo);
}
