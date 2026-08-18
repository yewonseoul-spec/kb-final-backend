package org.scoula.admin.service;

import org.scoula.admin.domain.AiPromptVO;

import java.util.List;

public interface PromptService {

    /** 사용중인 프롬프트 본문. DB에 없거나 조회 실패면 코드 기본값을 돌려준다. */
    String get(String promptKey);

    /**
     * DB 값이 없으면 넘겨받은 기본값을 쓴다.
     *
     * 다른 도메인 기능은 자기 프롬프트 상수를 이미 갖고 있으므로
     * 그 상수를 그대로 폴백으로 넘기게 한다.
     * 이렇게 하면 기존 상수 파일을 지우지 않아도 되고,
     * 프롬프트 관리 기능이 죽어도 그쪽 기능은 원래대로 동작한다.
     */
    String getOrDefault(String promptKey, String fallback);

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