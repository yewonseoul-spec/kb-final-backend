package org.scoula.admin.mapper;

import org.apache.ibatis.annotations.*;
import org.scoula.admin.domain.AiPromptVO;

import java.util.List;

public interface AiPromptMapper {

    /**
     * 현재 사용중인 프롬프트 본문.
     * is_active='Y' 가 실수로 둘 이상이어도 최신 버전 하나만 쓰도록 방어한다.
     */
    @Select("SELECT content FROM ai_prompt " +
            "WHERE prompt_key = #{promptKey} AND is_active = 'Y' " +
            "ORDER BY version DESC LIMIT 1")
    String findActiveContent(@Param("promptKey") String promptKey);

    /** 재현성 기록용. 어느 버전으로 분석했는지 Candidate 에 남긴다 */
    @Select("SELECT version FROM ai_prompt " +
            "WHERE prompt_key = #{promptKey} AND is_active = 'Y' " +
            "ORDER BY version DESC LIMIT 1")
    Integer findActiveVersion(@Param("promptKey") String promptKey);

    @Select("SELECT prompt_no, prompt_key, version, content, memo, is_active, member_no, created_at " +
            "FROM ai_prompt WHERE prompt_key = #{promptKey} ORDER BY version DESC")
    List<AiPromptVO> findVersions(@Param("promptKey") String promptKey);

    @Select("SELECT prompt_no, prompt_key, version, content, memo, is_active, member_no, created_at " +
            "FROM ai_prompt WHERE prompt_no = #{promptNo}")
    AiPromptVO findByNo(@Param("promptNo") int promptNo);

    /**
     * 키와 버전을 지정해 한 행을 가져온다.
     *
     * 사용중이 아닌 버전으로도 분석을 돌려야 할 때 쓴다.
     * 본문과 버전 번호를 같은 행에서 한 번에 얻어야
     * "v3 로 기록했는데 실제로는 v2 본문으로 분석" 같은 어긋남이 생기지 않는다.
     */
    @Select("SELECT prompt_no, prompt_key, version, content, memo, is_active, member_no, created_at " +
            "FROM ai_prompt WHERE prompt_key = #{promptKey} AND version = #{version}")
    AiPromptVO findByKeyAndVersion(@Param("promptKey") String promptKey,
                                   @Param("version") Integer version);

    @Select("SELECT DISTINCT prompt_key FROM ai_prompt ORDER BY prompt_key")
    List<String> findKeys();

    @Select("SELECT IFNULL(MAX(version), 0) + 1 FROM ai_prompt WHERE prompt_key = #{promptKey}")
    int findNextVersion(@Param("promptKey") String promptKey);

    @Insert("INSERT INTO ai_prompt (prompt_key, version, content, memo, is_active, member_no) " +
            "VALUES (#{promptKey}, #{version}, #{content}, #{memo}, 'N', #{memberNo})")
    @Options(useGeneratedKeys = true, keyProperty = "promptNo")
    int insertPrompt(AiPromptVO vo);

    @Update("UPDATE ai_prompt SET is_active = 'N' WHERE prompt_key = #{promptKey}")
    int deactivateAll(@Param("promptKey") String promptKey);

    @Update("UPDATE ai_prompt SET is_active = 'Y' WHERE prompt_no = #{promptNo}")
    int activate(@Param("promptNo") int promptNo);

    @Select("SELECT COUNT(*) FROM ai_prompt WHERE prompt_key = #{promptKey}")
    int countByKey(@Param("promptKey") String promptKey);

    @Delete("DELETE FROM ai_prompt WHERE prompt_no = #{promptNo} AND is_active = 'N'")
    int deletePrompt(@Param("promptNo") int promptNo);
}