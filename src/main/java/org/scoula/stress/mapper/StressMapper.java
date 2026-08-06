package org.scoula.stress.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.scoula.stress.domain.StressScenarioVO;
import org.scoula.stress.dto.CategorySpendingDto;

import java.util.List;

public interface StressMapper {

    /**
     * stress-01: 시나리오 전체 조회.
     * 화면 노출 순서를 고정하기 위해 코드 순서를 직접 지정한다.
     * shock_level은 ENUM이라 문자열 정렬하면 HIGH < LOW < MID가 되므로 FIELD로 강제한다.
     */
    @Select("SELECT scenario_no, scenario_code, scenario_name, description, " +
            "       shock_level, target_category, change_rate, fixed_amount " +
            "FROM stress_scenario " +
            "ORDER BY FIELD(scenario_code, 'INFLATION','RENT','RATE','MEDICAL','COMPLEX'), " +
            "         FIELD(shock_level, 'LOW','MID','HIGH')")
    List<StressScenarioVO> findAllScenarios();

    /** stress-02: 시나리오 코드 + 강도로 한 건 조회 */
    @Select("SELECT scenario_no, scenario_code, scenario_name, description, " +
            "       shock_level, target_category, change_rate, fixed_amount " +
            "FROM stress_scenario " +
            "WHERE scenario_code = #{scenarioCode} AND shock_level = #{shockLevel}")
    StressScenarioVO findScenario(@Param("scenarioCode") String scenarioCode,
                                  @Param("shockLevel") String shockLevel);

    /** stress-02: COMPLEX 계산용. 같은 강도의 다른 시나리오를 모두 가져온다 */
    @Select("SELECT scenario_no, scenario_code, scenario_name, description, " +
            "       shock_level, target_category, change_rate, fixed_amount " +
            "FROM stress_scenario " +
            "WHERE shock_level = #{shockLevel} AND scenario_code <> 'COMPLEX' " +
            "ORDER BY FIELD(scenario_code, 'INFLATION','RENT','RATE','MEDICAL')")
    List<StressScenarioVO> findScenariosByLevel(@Param("shockLevel") String shockLevel);

    /**
     * stress-02: 카테고리별 월평균 지출.
     *
     * 룰북 기준은 최근 6개월이다. 데이터가 그보다 적으면 실제 있는 개월 수로 나뉘므로
     * 기간이 짧아도 월평균의 의미는 유지된다.
     * month_count를 함께 돌려주어 몇 개월치로 계산했는지 근거에 표시한다.
     */
    @Select("SELECT sc.category_name AS categoryName, " +
            "       ROUND(SUM(s.amount) / " +
            "             COUNT(DISTINCT DATE_FORMAT(s.spending_date, '%Y-%m'))) AS monthlyAmount, " +
            "       COUNT(DISTINCT DATE_FORMAT(s.spending_date, '%Y-%m')) AS monthCount " +
            "FROM spending s " +
            "JOIN spending_category sc ON sc.category_no = s.category_no " +
            "WHERE s.member_no = #{memberNo} " +
            "  AND s.spending_date >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH) " +
            "GROUP BY sc.category_name")
    List<CategorySpendingDto> findMonthlySpending(@Param("memberNo") int memberNo);

    /**
     * stress-02: 사용 가능 잔액.
     *
     * account에 계좌 유형(입출금/예적금) 구분 컬럼이 없어 전 계좌 합계를 쓴다.
     * 예적금이 섞여 있어도 구분할 수 없다는 것이 알려진 한계다.
     */
    @Select("SELECT COALESCE(SUM(balance), 0) FROM account WHERE member_no = #{memberNo}")
    long findTotalBalance(@Param("memberNo") int memberNo);

    /** stress-02: 의료비 기대값 산출용 만 나이. 프로필이 없으면 null */
    @Select("SELECT TIMESTAMPDIFF(YEAR, birth_date, CURDATE()) " +
            "FROM member_profile WHERE member_no = #{memberNo}")
    Integer findMemberAge(@Param("memberNo") int memberNo);
}