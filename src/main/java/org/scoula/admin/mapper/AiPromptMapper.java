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