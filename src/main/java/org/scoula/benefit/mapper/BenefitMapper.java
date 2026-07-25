package org.scoula.benefit.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.scoula.benefit.domain.BenefitVO;

@Mapper
public interface BenefitMapper {
    int upsertBenefit(BenefitVO benefit);

    Integer findBenefitNoByPlcyNo(String plcyNo);

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
}