package org.scoula.consumption.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 소비 분석 프롬프트 시험 실행 결과.
 *
 * 관리자 화면(/admin/prompt)에서 저장하지 않은 프롬프트로 돌려보는 용도다.
 * DB 에 아무것도 쓰지 않는다.
 *
 * ★ 검증이 두 개라는 점이 중요하다.
 *   규칙 검증(RuleBasedValidator) — 실제 문지기. 실패하면 재시도하고, 끝내 실패하면 오류 문구가 나간다
 *   AI 검증(AiVerificationService) — 참고용 품질 코멘트. 실패해도 결과는 그대로 나간다
 * 화면에 둘 다 보여야 관리자가 "통과했다"를 잘못 읽지 않는다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumptionPromptTestDTO {

    /** 실제 분석에 쓰인 입력. 화면이 이걸 파싱해 입력 요약 표를 그린다 */
    private String summaryJson;

    /** 시도별 이력. MAX_ATTEMPTS 만큼 들어갈 수 있다 */
    private List<Attempt> attempts;

    /** 최종적으로 규칙 검증을 통과했는가. 실패면 사용자에게 오류 문구가 나간다 */
    private boolean finallyPassed;

    /** 최종 채택된 분석 결과 JSON. 실패했으면 null */
    private String finalContent;

    /** AI 검증 결과. 규칙 검증을 통과한 경우에만 실행된다 */
    private boolean aiVerdictPassed;
    private String aiVerdictReason;

    /** 전체 소요 시간 */
    private long durationMs;

    /** 호출 자체가 실패한 경우. 정상이면 null */
    private String errorMsg;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Attempt {
        /** 몇 번째 시도인가. 1부터 */
        private int no;

        /** 이 시도에서 AI 가 돌려준 원문 */
        private String content;

        /** 규칙 검증 통과 여부 */
        private boolean rulePassed;

        /**
         * 위반 사유 목록.
         * RuleBasedValidator 는 여러 개를 " / " 로 이어 한 문장으로 만드는데,
         * 그대로 두면 화면에서 읽을 수가 없어 나눠서 담는다.
         */
        private List<String> violations;
    }
}
