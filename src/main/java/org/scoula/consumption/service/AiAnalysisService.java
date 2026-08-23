package org.scoula.consumption.service;

import org.scoula.consumption.dto.ConsumptionPromptTestDTO;

public interface AiAnalysisService {
    // 회원(memberNo)의 요청에 대해 summaryText(이번 달 소비 요약)을 보내고 분석 결과(JSON 문자열)을 받는다
    String analyze(Integer memberNo, String summaryText);

    /**
     * [상호 추가] 관리자 프롬프트 시험 실행용.
     *
     * 저장하지 않은 프롬프트로 돌려보기 위한 메서드입니다. 기존 analyze() 와 세 가지가 다릅니다.
     *   1. 회원 캐시를 타지 않습니다. 시험은 몇 번을 돌려도 매번 실행돼야 합니다
     *   2. 분석·검증 프롬프트를 인자로 받습니다
     *   3. 최종 결과만이 아니라 시도별 이력을 함께 돌려줍니다
     *      (1차에 실패하고 2차에 통과했는지, 두 번 다 실패했는지가 화면에서 구분돼야 합니다)
     *
     * DB 에 아무것도 쓰지 않습니다.
     */
    ConsumptionPromptTestDTO testAnalyze(String summaryJson,
                                         String analysisPrompt,
                                         String verificationPrompt);
}
