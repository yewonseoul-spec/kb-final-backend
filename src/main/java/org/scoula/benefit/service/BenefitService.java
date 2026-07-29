package org.scoula.benefit.service;

import org.scoula.benefit.dto.BenefitCategoryResDto;
import org.scoula.benefit.dto.YouthPolicyRequestDTO;

import java.util.List;

public interface BenefitService {
    String getYouthPolicyRaw(YouthPolicyRequestDTO requestDTO);
    int syncYouthPolicies(YouthPolicyRequestDTO requestDTO);

    //scheduler
    int syncDailyYouthPolicies();

    //관리자 기간별 동기화
    int syncYouthPoliciesByFrstRegDt(String startDate, String endDate);

    //필터
    List<BenefitCategoryResDto> findBenefitCategories();
}