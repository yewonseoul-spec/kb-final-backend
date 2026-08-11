package org.scoula.stress.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.scoula.stress.domain.StressScenarioVO;
import org.scoula.stress.dto.CategoryMonthlyResDto;

import java.util.List;

/**
 * 스트레스 테스트 조회
 * 소비 조회는 카테고리별 월별 합계를 그대로 돌려주고 월 환산은 자바에서 한다.
 * GROUP BY 는 거래가 없는 달의 행을 만들어주지 않으므로 SQL 에서 나누면
 * 거래가 있었던 달의 수로 나누게 되어 산발적 지출이 부풀려지기 때문이다.
 * @fileName        : StressMapper
 * @author          : 박상호
 * @since           : 2026-08-12
 */
public interface StressMapper {

    /**
     * 시나리오 전체를 조회한다
     * 화면 노출 순서를 고정하기 위해 코드 순서를 직접 지정한다.
     * shock_level 은 ENUM 이라 문자열 정렬하면 순서가 어긋나므로 FIELD 로 강제한다.
     */
    @Select("SELECT scenario_no, scenario_code, scenario_name, description, " +
            "       shock_level, target_category, change_rate, fixed_amount " +
            "FROM stress_scenario " +
            "ORDER BY FIELD(scenario_code, 'INFLATION','RENT','RATE','MEDICAL','COMPLEX'), " +
            "         FIELD(shock_level, 'LOW','MID','HIGH')")
    List<StressScenarioVO> findAllScenarios();

    /**
     * 시나리오 코드와 강도로 한 건을 조회한다
     */
    @Select("SELECT scenario_no, scenario_code, scenario_name, description, " +
            "       shock_level, target_category, change_rate, fixed_amount " +
            "FROM stress_scenario " +
            "WHERE scenario_code = #{scenarioCode} AND shock_level = #{shockLevel}")
    StressScenarioVO findScenario(@Param("scenarioCode") String scenarioCode,
                                  @Param("shockLevel") String shockLevel);

    /**
     * 같은 강도의 다른 시나리오를 모두 조회한다
     */
    @Select("SELECT scenario_no, scenario_code, scenario_name, description, " +
            "       shock_level, target_category, change_rate, fixed_amount " +
            "FROM stress_scenario " +
            "WHERE shock_level = #{shockLevel} AND scenario_code <> 'COMPLEX' " +
            "ORDER BY FIELD(scenario_code, 'INFLATION','RENT','RATE','MEDICAL')")
    List<StressScenarioVO> findScenariosByLevel(@Param("shockLevel") String shockLevel);

    /**
     * 카테고리별 월별 소비 합계를 조회한다
     * 조회 구간은 반열린 구간이다. 시작일은 포함하고 종료일은 포함하지 않는다.
     * 거래가 없는 달은 행 자체가 나오지 않으므로 자바에서 0 원으로 채운다.
     */
    @Select("SELECT DATE_FORMAT(s.spending_date, '%Y-%m') AS yearMonth, " +
            "       sc.category_name AS categoryName, " +
            "       SUM(s.amount) AS amount " +
            "FROM spending s " +
            "JOIN spending_category sc ON sc.category_no = s.category_no " +
            "WHERE s.member_no = #{memberNo} " +
            "  AND s.spending_date >= #{startDate} " +
            "  AND s.spending_date < #{endDate} " +
            "GROUP BY DATE_FORMAT(s.spending_date, '%Y-%m'), sc.category_no, sc.category_name")
    List<CategoryMonthlyResDto> findCategoryMonthlySpending(@Param("memberNo") int memberNo,
                                                            @Param("startDate") String startDate,
                                                            @Param("endDate") String endDate);

    /**
     * 소비 데이터가 처음 나타난 달을 조회한다
     * 데이터 수집 시작 이전은 0 원이 아니라 모르는 기간이므로 분석 창에서 제외하기 위해 쓴다.
     * 거래가 하나도 없으면 null 을 반환한다.
     */
    @Select("SELECT DATE_FORMAT(MIN(spending_date), '%Y-%m') " +
            "FROM spending WHERE member_no = #{memberNo}")
    String findFirstSpendingMonth(@Param("memberNo") int memberNo);

    /**
     * 등록된 계좌 수를 조회한다
     * 계좌가 0 개인 것과 잔액이 0 원인 것은 다르다. 계좌를 연결하지 않았다고 해서
     * 재산이 0 원이라는 뜻이 아니므로 잔액 합계가 아니라 계좌 수로 판단한다.
     */
    @Select("SELECT COUNT(*) FROM account WHERE member_no = #{memberNo}")
    int findAccountCount(@Param("memberNo") int memberNo);

    /**
     * 등록 계좌 잔액 합계를 조회한다
     * account 에 계좌 유형 구분 컬럼이 없어 전 계좌 합계를 쓴다.
     * 실제 즉시 사용 가능한 자금과 다를 수 있다는 것이 알려진 한계다.
     */
    @Select("SELECT COALESCE(SUM(balance), 0) FROM account WHERE member_no = #{memberNo}")
    long findTotalBalance(@Param("memberNo") int memberNo);

    /**
     * 등록 월 소득을 조회한다
     * 값이 없으면 null 을 반환한다. 0 으로 대체하지 않는다.
     */
    @Select("SELECT income FROM member_profile WHERE member_no = #{memberNo}")
    Long findMonthlyIncome(@Param("memberNo") int memberNo);

    /**
     * 만 나이를 조회한다. 프로필이 없으면 null 을 반환한다
     */
    @Select("SELECT TIMESTAMPDIFF(YEAR, birth_date, CURDATE()) " +
            "FROM member_profile WHERE member_no = #{memberNo}")
    Integer findMemberAge(@Param("memberNo") int memberNo);


}