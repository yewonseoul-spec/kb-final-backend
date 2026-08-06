package org.scoula.mypage.mapper;

import org.apache.ibatis.annotations.Param;
import org.scoula.mypage.dto.AppliedBenefitDTO;

import java.util.List;

public interface AppliedBenefitMapper {
    List<AppliedBenefitDTO> findByMemberNo(int memberNo);

    int delete(@Param("memberNo") int memberNo, @Param("benefitNo") int benefitNo);

    int insert(@Param("memberNo") int memberNo, @Param("benefitNo") int benefitNo);
}