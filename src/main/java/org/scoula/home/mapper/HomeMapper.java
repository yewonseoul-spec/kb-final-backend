package org.scoula.home.mapper;

import org.scoula.home.dto.PopularBenefitDTO;

import java.util.List;

public interface HomeMapper {
    List<PopularBenefitDTO> findPopularBenefits(int limit);
}