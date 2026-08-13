package org.scoula.benefit.service;

import org.scoula.benefit.dto.*;

import java.util.List;

import org.scoula.benefit.dto.BenefitDetailResDTO;

public interface BenefitService {
    String getYouthPolicyRaw(YouthPolicyRequestDTO requestDTO);

    int syncYouthPolicies(YouthPolicyRequestDTO requestDTO);

    //scheduler
    int syncDailyYouthPolicies();

    //관리자 기간별 동기화
    int syncYouthPoliciesByFrstRegDt(String startDate, String endDate);

    //혜택 조회

    List<BenefitListResDTO> findBenefit(BenefitFilterReqDTO filter);

    //카테고리 필터
    List<BenefitCategoryResDTO> findBenefitCategories();

    //지역 필터
    List<BenefitRegionResDTO> findRegion(String parentRegionCode);

    //전공 필터
    List<BenefitMajorResDTO> findBenefitMajors();

    //학력 필터
    List<BenefitSchoolResDTO> findBenefitSchools();

    //직업 필터
    List<BenefitJobResDTO> findBenefitJobs();

    //혼인 필터
    List<BenefitMarriageResDTO> findBenefitMarriage();

    //검색창
    List<RecommendedKeywordResDTO>
    findRecommendedKeywords();

    List<String> findRecentKeywords(
            Integer memberNo
    );

    void saveRecentKeyword(
            Integer memberNo,
            String keyword
    );

    void deleteRecentKeyword(
            Integer memberNo,
            String keyword
    );

    void deleteAllRecentKeywords(
            Integer memberNo
    );
    // 관리자 기간별 동기화 (처리 내역 포함)
    SyncDetailResultDTO syncByFrstRegDtWithDetail(String startDate, String endDate);

//혜택 상세페이지
BenefitDetailResDTO findBenefitDetail(
        Integer benefitNo
);

//사용자 프로필 조건기반 혜택추천
BenefitProfileFilterResDTO findBenefitProfileFilter(
        Integer memberNo
);

//소비 기반 혜택 추천
ConsumptionRecommendResDTO
findConsumptionRecommendedBenefits(
        Integer memberNo,
        BenefitFilterReqDTO filter
);

}