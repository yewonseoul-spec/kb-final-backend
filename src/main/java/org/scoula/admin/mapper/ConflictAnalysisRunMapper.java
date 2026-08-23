package org.scoula.admin.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.scoula.admin.domain.ConflictAnalysisRunVO;

import java.util.List;
import java.util.Map;

public interface ConflictAnalysisRunMapper {

    String COLUMNS =
            " run_no, source_benefit_no, prompt_key, prompt_version, source_text_hash," +
            " model_name, extraction_status, canonical_status, is_current," +
            " relations_extracted, observations_saved, observations_failed, candidates_written," +
            " failure_reason, started_at, completed_at ";

    // ------------------------------------------------------------
    // 실행 시작
    // ------------------------------------------------------------

    /**
     * 분석을 시작하면서 기록을 남긴다.
     *
     * AI 를 부르기 전에 남기는 이유는, 호출 도중 서버가 죽어도
     * 시도했다는 사실이 남아야 하기 때문이다.
     * 기록이 없으면 중단된 분석과 하지 않은 분석을 구분할 수 없다.
     */
    @Insert("INSERT INTO benefit_conflict_analysis_run (" +
            " source_benefit_no, prompt_key, prompt_version, source_text_hash," +
            " model_name, extraction_status, canonical_status" +
            ") VALUES (" +
            " #{sourceBenefitNo}, #{promptKey}, #{promptVersion}, #{sourceTextHash}," +
            " #{modelName}, 'STARTED', 'PENDING')")
    @Options(useGeneratedKeys = true, keyProperty = "runNo")
    int insertRun(ConflictAnalysisRunVO vo);

    /**
     * 병행 기록 전용. 정리 대상이 되지 않도록 처음부터 선택 제외로 남긴다.
     *
     * 이 실행은 "AI 가 무엇을 반환했는지 기록해 두는 것" 까지만 한다.
     * 후보를 만들지도, 현재 세대가 되지도 않는다.
     *
     * canonical_status 를 PENDING 으로 두면 안 된다.
     * findReconcilableRun 이 PENDING 과 FAILED 를 정리 대기로 보므로,
     * 나중에 정리 단계를 붙이는 순간 과거 병행 기록이 한꺼번에
     * 정리 대상으로 딸려 들어온다.
     * 그때는 이미 후보가 다른 경로로 저장돼 있어 무엇이 맞는지 알 수 없다.
     *
     * 그래서 일반 실행과 SQL 자체를 나눈다.
     * 호출하는 쪽이 상태를 넘기게 하면 언젠가 잘못 넘기게 된다.
     */
    @Insert("INSERT INTO benefit_conflict_analysis_run (" +
            " source_benefit_no, prompt_key, prompt_version, source_text_hash," +
            " model_name, extraction_status, canonical_status" +
            ") VALUES (" +
            " #{sourceBenefitNo}, #{promptKey}, #{promptVersion}, #{sourceTextHash}," +
            " #{modelName}, 'STARTED', 'NOT_SELECTED')")
    @Options(useGeneratedKeys = true, keyProperty = "runNo")
    int insertShadowRun(ConflictAnalysisRunVO vo);

    // ------------------------------------------------------------
    // 추출 결과 기록
    // ------------------------------------------------------------

    /**
     * 관측을 전부 저장했다.
     *
     * 관계가 하나도 없는 응답도 여기로 온다.
     * AI 가 정상적으로 읽고 관계가 없다고 답한 것도 완료된 분석이고,
     * 그것을 기록해야 다음 동기화에서 같은 정책을 다시 부르지 않는다.
     */
    @Update("UPDATE benefit_conflict_analysis_run SET " +
            "  extraction_status = 'OBSERVATIONS_READY'," +
            "  relations_extracted = #{extracted}, observations_saved = #{saved}," +
            "  observations_failed = 0 " +
            "WHERE run_no = #{runNo} AND extraction_status = 'STARTED'")
    int markObservationsReady(@Param("runNo") int runNo,
                              @Param("extracted") int extracted,
                              @Param("saved") int saved);

    /**
     * 관측 일부만 저장됐다.
     *
     * 이 실행은 현재 세대가 될 수 없다.
     * 금지 근거는 저장되고 허용 근거가 저장에 실패했다면
     * 남은 근거만으로 자동 차단이 성립할 수 있기 때문이다.
     */
    @Update("UPDATE benefit_conflict_analysis_run SET " +
            "  extraction_status = 'PARTIAL'," +
            "  relations_extracted = #{extracted}, observations_saved = #{saved}," +
            "  observations_failed = #{failed}, failure_reason = #{reason}," +
            "  completed_at = NOW() " +
            "WHERE run_no = #{runNo} AND extraction_status = 'STARTED'")
    int markPartial(@Param("runNo") int runNo,
                    @Param("extracted") int extracted,
                    @Param("saved") int saved,
                    @Param("failed") int failed,
                    @Param("reason") String reason);

    @Update("UPDATE benefit_conflict_analysis_run SET " +
            "  extraction_status = 'FAILED', failure_reason = #{reason}, completed_at = NOW() " +
            "WHERE run_no = #{runNo} AND extraction_status = 'STARTED'")
    int markExtractionFailed(@Param("runNo") int runNo, @Param("reason") String reason);

    // ------------------------------------------------------------
    // 현재 세대 선택
    // ------------------------------------------------------------

    /**
     * 이 정책 · 이 세대의 기존 현재 표시를 뗀다.
     *
     * 공고문이 바뀌어 새 실행이 현재가 될 때만 부른다.
     * 다음의 claimCurrent 와 같은 트랜잭션 안에서 실행해야
     * 현재 세대가 잠시 없는 상태가 밖으로 보이지 않는다.
     */
    @Update("UPDATE benefit_conflict_analysis_run SET is_current = NULL " +
            "WHERE source_benefit_no = #{sourceBenefitNo} " +
            "  AND prompt_key = #{promptKey} " +
            "  AND prompt_version = #{promptVersion} " +
            "  AND is_current = 'Y'")
    int releaseCurrent(@Param("sourceBenefitNo") int sourceBenefitNo,
                       @Param("promptKey") String promptKey,
                       @Param("promptVersion") Integer promptVersion);

    /**
     * 이 실행을 현재 세대로 잡는다.
     *
     * 같은 조합에 이미 현재가 있으면 uk_run_current 에 걸려 예외가 난다.
     * 두 실행이 동시에 끝났을 때 먼저 잡은 쪽이 유지되는 것은 그 제약 때문이다.
     * 늦게 끝난 실행이 이미 선택된 세대를 밀어내면 실행 근거가 도중에 바뀐다.
     *
     * 관측을 전부 저장한 실행만 현재가 될 수 있다.
     * 일부만 저장된 실행은 근거가 빠져 있으므로 조건에서 막는다.
     * 반환값이 0 이면 그 이유로 잡지 못한 것이다.
     */
    @Update("UPDATE benefit_conflict_analysis_run SET " +
            "  is_current = 'Y', canonical_status = 'READY'," +
            "  candidates_written = #{candidatesWritten}, completed_at = NOW() " +
            "WHERE run_no = #{runNo} " +
            "  AND extraction_status = 'OBSERVATIONS_READY' " +
            "  AND is_current IS NULL")
    int claimCurrent(@Param("runNo") int runNo,
                     @Param("candidatesWritten") int candidatesWritten);

    /**
     * 정리나 후보 저장에 실패했다.
     * 관측은 남아 있으므로 AI 를 다시 부르지 않고 정리만 재시도한다.
     */
    @Update("UPDATE benefit_conflict_analysis_run SET " +
            "  canonical_status = 'FAILED', failure_reason = #{reason} " +
            "WHERE run_no = #{runNo}")
    int markCanonicalFailed(@Param("runNo") int runNo, @Param("reason") String reason);

    /** 같은 조합에서 다른 실행이 먼저 현재 세대가 됐다 */
    @Update("UPDATE benefit_conflict_analysis_run SET " +
            "  canonical_status = 'NOT_SELECTED', completed_at = NOW() " +
            "WHERE run_no = #{runNo} AND is_current IS NULL")
    int markNotSelected(@Param("runNo") int runNo);

    // ------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------

    /** 이 정책 · 이 세대에서 지금 실행에 쓰는 실행 */
    @Select("SELECT " + COLUMNS +
            "FROM benefit_conflict_analysis_run " +
            "WHERE source_benefit_no = #{sourceBenefitNo} " +
            "  AND prompt_key = #{promptKey} " +
            "  AND prompt_version = #{promptVersion} " +
            "  AND is_current = 'Y'")
    ConflictAnalysisRunVO findCurrentRun(@Param("sourceBenefitNo") int sourceBenefitNo,
                                         @Param("promptKey") String promptKey,
                                         @Param("promptVersion") Integer promptVersion);

    /**
     * AI 를 다시 부르지 않고 정리만 재시도할 수 있는 실행.
     *
     * 관측은 이미 저장돼 있는데 정리에서 실패한 경우다.
     * 이것을 찾지 못하면 같은 본문을 AI 에 다시 보내게 되어 비용이 두 번 나간다.
     *
     * 같은 조건의 실행이 여럿이면 나중 것을 쓴다.
     * 어느 것을 쓸지 정해두지 않으면 부를 때마다 다른 결과가 나온다.
     */
    @Select("SELECT " + COLUMNS +
            "FROM benefit_conflict_analysis_run " +
            "WHERE source_benefit_no = #{sourceBenefitNo} " +
            "  AND prompt_key = #{promptKey} " +
            "  AND prompt_version = #{promptVersion} " +
            "  AND source_text_hash = #{sourceTextHash} " +
            "  AND extraction_status = 'OBSERVATIONS_READY' " +
            "  AND canonical_status IN ('PENDING', 'FAILED') " +
            "ORDER BY run_no DESC LIMIT 1")
    ConflictAnalysisRunVO findReconcilableRun(@Param("sourceBenefitNo") int sourceBenefitNo,
                                             @Param("promptKey") String promptKey,
                                             @Param("promptVersion") Integer promptVersion,
                                             @Param("sourceTextHash") String sourceTextHash);

    @Select("SELECT " + COLUMNS +
            "FROM benefit_conflict_analysis_run WHERE run_no = #{runNo}")
    ConflictAnalysisRunVO findByNo(@Param("runNo") int runNo);

    /** 운영 화면용 집계 */
    @Select("SELECT extraction_status, canonical_status, COUNT(*) AS cnt " +
            "FROM benefit_conflict_analysis_run " +
            "WHERE prompt_version = #{promptVersion} " +
            "GROUP BY extraction_status, canonical_status ORDER BY cnt DESC")
    List<Map<String, Object>> summaryByVersion(@Param("promptVersion") Integer promptVersion);
}
