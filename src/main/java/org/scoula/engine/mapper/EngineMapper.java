package org.scoula.engine.mapper;

import org.scoula.engine.dto.BenefitResDto;
import org.scoula.engine.dto.ConflictWarningDto;
import org.scoula.engine.dto.UserProfileResDto;

import java.util.List;

public interface EngineMapper {

    // engine-01: 사용자 프로필 조회
    UserProfileResDto findUserProfile(int memberNo);

    // engine-01: 자격조건 필터링된 정책 목록 조회
    List<BenefitResDto> findEligibleBenefits(UserProfileResDto profile);


    // engine-02·03 예정: 보유 정책 번호 목록 조회
    // (현재 engine-02는 SQL 서브쿼리로 처리 중, 추후 사용 가능)
    List<Integer> findAppliedBenefitNos(int memberNo);


    //engine-03: 보유 정책과 충돌하는 경고 규칙 조회 (일부제한·확인필요)
    List<ConflictWarningDto> findConflictWarnings(List<Integer> appliedBenefitNos);
}
