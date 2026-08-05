package org.scoula.mypage.mapper;

import org.scoula.mypage.dto.AppliedBenefitDTO;

import java.util.List;

public interface AppliedBenefitMapper {
    List<AppliedBenefitDTO> findByMemberNo(int memberNo);
}