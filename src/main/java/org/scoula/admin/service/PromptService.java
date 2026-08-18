package org.scoula.admin.service;

import org.scoula.admin.domain.AiPromptVO;

import java.util.List;

public interface PromptService {

    /** 사용중인 프롬프트 본문. DB에 없거나 조회 실패면 코드 기본값을 돌려준다. */
    String get(String promptKey);

    /** 사용중인 버전 번호. 폴백으로 떨어졌으면 null */
    Integer getActiveVersion(String promptKey);

    List<AiPromptVO> getVersions(String promptKey);

    List<String> getKeys();

    /** 새 버전 저장. 저장만 하고 적용하지는 않는다. */
    int createVersion(String promptKey, String content, String memo, Integer memberNo);

    /** 해당 버전을 사용중으로 전환한다. */
    void activate(String promptKey, int promptNo);

    /** 버전 삭제. 사용중이거나 마지막 하나면 거부한다 */
    void deleteVersion(String promptKey, int promptNo);
}