package org.scoula.benefit.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.scoula.benefit.domain.BenefitVO;
import org.scoula.benefit.dto.*;
import org.scoula.benefit.dto.BenefitDetailResDTO;
import org.scoula.benefit.dto.BenefitProfileFilterResDTO;
import org.scoula.benefit.dto.ConsumptionCategoryResDTO;

import java.util.List;

@Mapper
public interface BenefitMapper {
    int upsertBenefit(BenefitVO benefit);

    Integer findBenefitNoByPlcyNo(String plcyNo);
    BenefitVO findBenefitByPlcyNo(String plcyNo);
    int deleteBenefitRegions(Integer benefitNo);
    int deleteBenefitMajors(Integer benefitNo);
    int deleteBenefitSchools(Integer benefitNo);
    int deleteBenefitJobs(Integer benefitNo);

    int insertBenefitRegion(
            @Param("benefitNo") Integer benefitNo,
            @Param("zipCd") String zipCd
    );

    int insertBenefitMajor(
            @Param("benefitNo") Integer benefitNo,
            @Param("plcyMajorCd") String plcyMajorCd
    );

    int insertBenefitSchool(
            @Param("benefitNo") Integer benefitNo,
            @Param("schoolCd") String schoolCd
    );

    int insertBenefitJob(
            @Param("benefitNo") Integer benefitNo,
            @Param("jobCd") String jobCd
    );

    int existsRegion(String zipCd);

    int existsBenefitByPlcyNo(String plcyNo);

    int updateBenefitStatusOnly(BenefitVO benefit);

    //온통청년 사라진 혜택 관리
    int deactivateBenefitsNotInApi(@Param("plcyNoList") List<String> plcyNoList);

    int restoreBenefitFromApi(String plcyNo);

   //카테고리 필터
   List<BenefitCategoryResDTO> findBenefitCategories();

   //검색 조회
    List<BenefitListResDTO> findBenefit(BenefitFilterReqDTO filter);

    //지역 필터
    List<BenefitRegionResDTO> findRegion(
            @Param("parentRegionCode") String parentRegionCode);

    //전공 필터
    List<BenefitMajorResDTO> findBenefitMajors();

    //학력 필터
    List<BenefitSchoolResDTO> findBenefitSchools();

    //직업 필터
    List<BenefitJobResDTO> findBenefitJobs();

    //혼인 필터
    List<BenefitMarriageResDTO> findBenefitMarriage();

    //검색창 키워드
    List<RecommendedKeywordResDTO>
    findRecommendedKeywords();

    //혜택 상세페이지
    BenefitDetailResDTO findBenefitDetail(
            Integer benefitNo

    );

    //사용자프로필 조건기반 혜택 추천
    BenefitProfileFilterResDTO findBenefitProfileFilter(
            @Param("memberNo") Integer memberNo
    );

    // 목표 → 혜택 중분류 매핑 조회
    List<String> findGoalCategoryCodes(
            @Param("goalType") String goalType,
            @Param("priority") int priority
    );

    // 목표 → 중분류 이름 조회 (화면 설명용)
    List<String> findGoalCategoryNames(
            @Param("goalType") String goalType,
            @Param("priority") int priority
    );
      
    //소비기반 혜택추천
    List<ConsumptionCategoryResDTO>
    findTopSpendingCategories(
            @Param("memberNo") Integer memberNo
    );

    List<String> findConsumptionBenefitCategoryNames(
            @Param("memberNo") Integer memberNo
    );

    List<BenefitListResDTO>
    findConsumptionRecommendedBenefits(
            @Param("memberNo")
            Integer memberNo,

            @Param("filter")
            BenefitFilterReqDTO filter
    );
}