package org.scoula.engine.mapper;

import org.scoula.engine.dto.BenefitResDto;
import org.scoula.engine.dto.UserProfileResDto;

import java.util.List;

public interface EngineMapper {

    UserProfileResDto findUserProfile(int memberNo);
    //사용자 프로필 조회

    List<BenefitResDto> findEligibleBenefits(UserProfileResDto profile);
    // 자격조건인데 필터링된 정책 목록 조회

    List<Integer> findAppliedBenefitNos(int memberNo);
    //사용자가 이미 보유한 정책 번호 목록
}
