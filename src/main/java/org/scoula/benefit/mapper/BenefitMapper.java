package org.scoula.benefit.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.scoula.benefit.domain.BenefitVO;
import org.scoula.benefit.dto.*;

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
}