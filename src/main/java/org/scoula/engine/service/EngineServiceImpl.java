package org.scoula.engine.service;

import lombok.RequiredArgsConstructor;
import org.scoula.engine.dto.BenefitResDto;
import org.scoula.engine.dto.UserProfileResDto;
import org.scoula.engine.mapper.EngineMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EngineServiceImpl implements EngineService{

    private final EngineMapper engineMapper;

    @Override
    public List<BenefitResDto> findEligibleBenefits(int memberNo) {
        UserProfileResDto profile = engineMapper.findUserProfile(memberNo);
        if (profile == null) {
            throw new RuntimeException("회원 프로필 없음. memberNo=" + memberNo);
        }
        List<BenefitResDto> benefits = engineMapper.findEligibleBenefits(profile);
        return benefits;
    }
}
