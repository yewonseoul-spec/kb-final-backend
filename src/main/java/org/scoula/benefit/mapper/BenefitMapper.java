package org.scoula.benefit.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.scoula.benefit.domain.BenefitVO;

@Mapper
public interface BenefitMapper {
    int upsertBenefit(BenefitVO benefit);
}