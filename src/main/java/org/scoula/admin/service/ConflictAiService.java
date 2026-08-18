package org.scoula.admin.service;

import org.scoula.admin.dto.ConflictAiItemDto;
import org.scoula.admin.dto.ConflictAiRunDto;

public interface ConflictAiService {

    /** 저장 없이 분류 결과와 통계만 돌려준다. limit <= 0 이면 전건 */
    ConflictAiRunDto dryRun(int offset, int limit);

    /** 분류 후 Resolver 를 거쳐 benefit_conflict_candidate 에 저장한다 */
    ConflictAiRunDto runAndSave(int offset, int limit);

    /**
     * 임의의 프롬프트로 정책 한 건을 돌려본다.
     * 활성 버전을 바꾸기 전에 결과를 확인하기 위한 것이라 DB 에 쓰지 않는다.
     */
    ConflictAiItemDto testPrompt(int benefitNo, String promptContent);
}