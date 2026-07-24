package org.scoula.engine.service;

import lombok.RequiredArgsConstructor;
import org.scoula.engine.dto.BenefitResDto;
import org.scoula.engine.dto.ConflictWarningDto;
import org.scoula.engine.dto.EngineResultDto;
import org.scoula.engine.dto.UserProfileResDto;
import org.scoula.engine.mapper.EngineMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EngineServiceImpl implements EngineService {

    private final EngineMapper engineMapper;

    @Override
    public EngineResultDto findEligibleBenefits(int memberNo) {
        // 1. 사용자 프로필 조회
        UserProfileResDto profile = engineMapper.findUserProfile(memberNo);
        if (profile == null) {
            throw new RuntimeException("회원 프로필 없음. memberNo=" + memberNo);
        }

        // 2. 자격조건 + 그룹 충돌 필터링된 정책 후보 조회 (engine-01·02)
        List<BenefitResDto> benefits = engineMapper.findEligibleBenefits(profile);

        // 3. 보유 정책 번호 목록 조회 (engine-03)
        List<Integer> appliedNos = engineMapper.findAppliedBenefitNos(memberNo);

        // 4. 경고 목록 조회 (engine-03)
        List<ConflictWarningDto> warnings = appliedNos.isEmpty()
                ? List.of()
                : engineMapper.findConflictWarnings(appliedNos);

        return new EngineResultDto(benefits, warnings);
    }
}
