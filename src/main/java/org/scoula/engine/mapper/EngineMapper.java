package org.scoula.engine.mapper;

import org.scoula.engine.dto.BenefitResDto;
import org.scoula.engine.dto.ConflictWarningDto;
import org.scoula.engine.dto.UserProfileResDto;

import java.util.List;

public interface EngineMapper {

    // engine-01: 사용자 프로필 조회
    UserProfileResDto findUserProfile(int memberNo);

    // engine-01·02: 자격조건 + 그룹충돌 필터링된 정책 목록 조회
    List<BenefitResDto> findEligibleBenefits(UserProfileResDto profile);

    // engine-03: 보유 정책 번호 목록 조회
    List<Integer> findAppliedBenefitNos(int memberNo);

    //engine-03: 보유 정책과 충돌하는 경고 규칙 조회 (일부제한·확인필요)
    List<ConflictWarningDto> findConflictWarnings(List<Integer> appliedBenefitNos);

    // engine-04: 외부 제도(실업급여 등) 충돌 경고 조회
    List<ConflictWarningDto> findExternalWarnings();
}
