package org.scoula.admin.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.scoula.admin.dto.BenefitNameDto;
import org.scoula.admin.dto.ConflictAiSourceDto;

import java.util.List;

public interface ConflictAiMapper {

    /**
     * 중복수혜 조항이 있을 가능성이 있는 정책. 실측 149건.
     *   가. "중복" 이 들어간 정책 전량            70
     *   나. "타/다른/유사 + 사업/제도/정책"        41
     *   다. "동시" 또는 "병행"                    19
     *
     * NOT 조건을 쓰지 않는다. 네 컬럼이 전부 NULL 이면 NOT (...) 이 NULL 이 되어
     * 그 행이 조용히 빠진다. 갈래별 합 130과 합집합 149의 차이가 이것이었다.
     *
     * is_active 로 좁히지 않는다. 룰북상 보유 정책 조회에는 is_active 필터를
     * 적용하지 않으므로 마감된 정책도 충돌 검사에 쓰인다.
     */
    @Select("SELECT benefit_no, plcy_nm, sprvsn_inst_cd_nm, " +
            "       plcy_sprt_cn, plcy_aply_mthd_cn, target_desc, earn_etc_cn, is_active " +
            "FROM benefit " +
            "WHERE api_deleted_yn = 'N' " +
            "  AND ( plcy_sprt_cn      LIKE '%중복%' " +
            "     OR plcy_aply_mthd_cn LIKE '%중복%' " +
            "     OR target_desc       LIKE '%중복%' " +
            "     OR earn_etc_cn       LIKE '%중복%' " +
            "     OR plcy_sprt_cn      REGEXP #{pattern} " +
            "     OR plcy_aply_mthd_cn REGEXP #{pattern} " +
            "     OR target_desc       REGEXP #{pattern} " +
            "     OR earn_etc_cn       REGEXP #{pattern} ) " +
            "ORDER BY benefit_no")
    List<ConflictAiSourceDto> findConflictCandidates(@Param("pattern") String pattern);

    /** Cross-check 용. 상대 정책은 후보 필터에 안 걸렸을 수 있어 번호로 직접 가져온다 */
    @Select("SELECT benefit_no, plcy_nm, sprvsn_inst_cd_nm, " +
            "       plcy_sprt_cn, plcy_aply_mthd_cn, target_desc, earn_etc_cn, is_active " +
            "FROM benefit WHERE benefit_no = #{benefitNo}")
    ConflictAiSourceDto findSourceByBenefitNo(@Param("benefitNo") int benefitNo);

    /**
     * 정책명 사전. 2,717건이라 통째로 메모리에 올려도 부담이 없다.
     * SQL 에서 정규화 비교를 하려면 REPLACE 중첩이 되어 읽기도 고치기도 어렵다.
     */
    @Select("SELECT benefit_no, plcy_nm, sprvsn_inst_cd_nm " +
            "FROM benefit WHERE api_deleted_yn = 'N' ORDER BY benefit_no")
    List<BenefitNameDto> findAllBenefitNames();
}