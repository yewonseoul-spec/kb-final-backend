package org.scoula.consumption.service;

import org.scoula.consumption.dto.AiVerdictDTO;

public interface AiVerificationService {

    AiVerdictDTO verify(String requestJson, String responseJson);

    /**
     * [상호 추가] 관리자 프롬프트 시험 실행용.
     *
     * 저장하지 않은 프롬프트로 검증을 돌려보기 위해 프롬프트를 인자로 받는 형태를 추가했습니다.
     * systemPromptOverride 가 null 이거나 비어 있으면 기존 verify() 와 완전히 같게 동작합니다.
     * 기존 메서드는 손대지 않았습니다.
     */
    AiVerdictDTO verify(String requestJson, String responseJson, String systemPromptOverride);
}
