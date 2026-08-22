package org.scoula.admin.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.scoula.admin.domain.ConflictObservationVO;

import java.util.List;

public interface ConflictObservationMapper {

    String COLUMNS =
            " observation_no, run_no, observation_index, source_benefit_no, scope, relation," +
            " target_name_raw, target_category_raw, direction, timing, subject_scope," +
            " restriction_stage, combination_applicability, trigger_scope," +
            " condition_type, condition_text," +
            " evidence_text, evidence_verified, confidence, dedupe_key, created_at ";

    /**
     * AI 가 반환한 관계를 손대지 않고 저장한다.
     *
     * 후보 저장과 달리 INSERT IGNORE 를 쓰지 않는다.
     * 같은 의미가 여러 번 들어오는 것을 그대로 남겨야
     * AI 가 한 응답 안에서 흔들렸다는 사실을 볼 수 있기 때문이다.
     * 실패하면 예외가 나야 하고, 그것이 곧 일부만 저장됐다는 신호다.
     *
     * 상대 정책 번호나 판정 결과는 여기서 저장하지 않는다.
     * 그것들은 AI 가 공고문에서 읽은 내용이 아니라
     * 그 뒤 단계에서 우리가 만들어낸 값이다.
     */
    @Insert("INSERT INTO benefit_conflict_observation (" +
            " run_no, observation_index, source_benefit_no, scope, relation," +
            " target_name_raw, target_category_raw," +
            " direction, timing, subject_scope, restriction_stage," +
            " combination_applicability, trigger_scope," +
            " condition_type, condition_text," +
            " evidence_text, evidence_verified, confidence, dedupe_key" +
            ") VALUES (" +
            " #{runNo}, #{observationIndex}, #{sourceBenefitNo}, #{scope}, #{relation}," +
            " #{targetNameRaw}, #{targetCategoryRaw}," +
            " #{direction}, #{timing}, #{subjectScope}, #{restrictionStage}," +
            " #{combinationApplicability}, #{triggerScope}," +
            " #{conditionType}, #{conditionText}," +
            " #{evidenceText}, #{evidenceVerified}, #{confidence}, #{dedupeKey})")
    @Options(useGeneratedKeys = true, keyProperty = "observationNo")
    int insertObservation(ConflictObservationVO vo);

    /**
     * 한 실행의 관측 전부. 정리 단계의 입력이다.
     *
     * 순번으로 정렬하지만 정리 결과가 이 순서에 좌우되면 안 된다.
     * 정렬은 사람이 로그를 읽을 때를 위한 것이지
     * 어느 관측을 우선할지 정하기 위한 것이 아니다.
     */
    @Select("SELECT " + COLUMNS +
            "FROM benefit_conflict_observation " +
            "WHERE run_no = #{runNo} " +
            "ORDER BY observation_index")
    List<ConflictObservationVO> findByRun(@Param("runNo") int runNo);

    /**
     * 한 후보의 근거가 된 관측들.
     *
     * 관리자가 검수 화면에서 "AI 가 실제로 무엇을 말했는지" 를 볼 때 쓴다.
     * 정리 결과가 모르겠다로 나온 이유를 설명하려면 원본이 필요하다.
     */
    @Select("SELECT " + COLUMNS +
            "FROM benefit_conflict_observation " +
            "WHERE run_no = #{runNo} AND dedupe_key = #{dedupeKey} " +
            "ORDER BY observation_index")
    List<ConflictObservationVO> findByCandidateGroup(@Param("runNo") int runNo,
                                                     @Param("dedupeKey") String dedupeKey);

    @Select("SELECT COUNT(*) FROM benefit_conflict_observation WHERE run_no = #{runNo}")
    int countByRun(@Param("runNo") int runNo);
}
