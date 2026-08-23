package org.scoula.admin.service;

import org.scoula.admin.dto.ConflictAiItemDto;
import org.scoula.admin.dto.ConflictAiRunDto;

public interface ConflictAiService {

    /** 저장 없이 분류 결과와 통계만 돌려준다. limit <= 0 이면 전건 */
    ConflictAiRunDto dryRun(int offset, int limit);

    /** 분류 후 Resolver 를 거쳐 benefit_conflict_candidate 에 저장한다 */
    ConflictAiRunDto runAndSave(int offset, int limit);

    /**
     * 버전을 지정해 분석하고 저장한다.
     *
     * 사용중이 아닌 버전으로도 돌릴 수 있다.
     * 새 프롬프트를 적용하기 전에 결과를 먼저 쌓아 확인하려면
     * 활성 버전을 바꾸지 않고 분석할 수 있어야 한다.
     * 분석에 쓰는 본문과 Candidate 에 적히는 버전은 같은 행에서 나온다.
     */
    ConflictAiRunDto runAndSaveVersion(Integer version, int offset, int limit);

    /**
     * 상대 정책 하나를 분석해 저장한다.
     * 후보 필터와 무관하게 호출되며, 이미 분석된 정책이면 건너뛴다.
     */
    int analyzeOne(int benefitNo);

    /**
     * 버전을 지정해 상대 정책 하나를 분석한다.
     *
     * 상호 대조는 같은 세대끼리 해야 하므로
     * 상대가 그 세대로 분석되지 않았으면 그 세대로 분석해야 한다.
     */
    int analyzeOneVersion(int benefitNo, Integer version);

    /**
     * 임의의 프롬프트로 정책 한 건을 돌려본다.
     * 활성 버전을 바꾸기 전에 결과를 확인하기 위한 것이라 DB 에 쓰지 않는다.
     *
     * [상호 수정] 입력을 benefit_no → plcy_no 로 바꿨다.
     *            benefit_no 는 재적재하면 재배정되어 화면의 예시 번호가 무효가 된다.
     *            plcy_no 는 20자리 숫자 문자열이므로 String 으로 받는다.
     */
    ConflictAiItemDto testPrompt(String plcyNo, String promptContent);

    /**
     * 후보는 저장하지 않고 AI 가 말한 관계만 기록한다.
     *
     * 저장 경로를 바꾸기 전에 기록 쪽이 실제로 도는지 확인하기 위한 것이다.
     * 기존 실행으로 확인하면 후보가 또 쌓이는데, 지금 고치려는 것이 그 쌓임이다.
     *
     * 여기서 남긴 기록은 아직 아무도 읽지 않는다.
     * 상호 대조도 판정도 규칙 생성도 이 값을 보지 않는다.
     */
    ConflictAiRunDto shadowRun(int offset, int limit);
}