package org.scoula.home.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.scoula.home.dto.HomeSummaryDTO;
import org.scoula.home.mapper.HomeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    // 화면이 TOP3 고정이라 파라미터로 열지 않는다
    private static final int POPULAR_LIMIT = 3;

    private final HomeMapper mapper;

    @Transactional(readOnly = true)
    @Override
    public HomeSummaryDTO getSummary() {
        HomeSummaryDTO summary = new HomeSummaryDTO();
        summary.setPopularBenefits(mapper.findPopularBenefits(POPULAR_LIMIT));
        return summary;
    }
}