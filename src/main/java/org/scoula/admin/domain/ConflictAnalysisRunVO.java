package org.scoula.admin.domain;

import lombok.Data;

import java.util.Date;

/**
 * 정책 하나를 한 프롬프트 세대 · 한 본문으로 분석한 실행 기록.
 *
 * 지금까지는 후보 행이 있는지로 "이 정책을 분석했는가" 를 판단했다.
 * 그러면 AI 가 정상적으로 읽고 "관계 없음" 을 반환한 경우를 표현할 수 없어
 * 매 동기화마다 같은 정책을 다시 AI 에 보내게 된다.
 * 반대로 관계 셋 중 하나만 저장에 성공해도 행이 생기므로
 * 불완전한 상태를 완료로 읽는다.
 *
 * 상태를 두 축으로 나눈 이유는 이 둘이 서로 다른 질문이기 때문이다.
 *
 *     extractionStatus  AI 를 다시 불러야 하는가
 *     canonicalStatus   이 실행 결과를 실행 근거로 써도 되는가
 *
 * 하나로 합치면 AI 응답은 받았는데 정리에 실패한 상태에서
 * 다시 부를지 말지를 정할 수 없다.
 * 관측이 이미 저장돼 있으므로 그 경우에는 정리만 다시 하면 된다.
 *
 * @author 박상호
 * @since 2026-08-21
 */
@Data
public class ConflictAnalysisRunVO {

    // ------------------------------------------------------------
    // extractionStatus — AI 재호출 판단
    // ------------------------------------------------------------

    /** 분석을 시작했고 아직 결과를 저장하지 않았다 */
    public static final String STARTED = "STARTED";

    /**
     * AI 응답을 받아 관측을 전부 저장했다.
     * 관계가 하나도 없는 응답도 여기 해당한다.
     * AI 가 정상적으로 읽고 관계가 없다고 답한 것도 완료된 분석이다.
     */
    public static final String OBSERVATIONS_READY = "OBSERVATIONS_READY";

    /**
     * 관측 일부만 저장됐다.
     *
     * 이 실행은 실행 근거가 될 수 없다.
     * 금지 근거는 저장되고 허용 근거가 저장에 실패했다면
     * 남은 근거만으로 자동 차단이 성립할 수 있기 때문이다.
     */
    public static final String PARTIAL = "PARTIAL";

    /** AI 호출이나 응답 해석이 실패했다 */
    public static final String FAILED = "FAILED";

    // ------------------------------------------------------------
    // canonicalStatus — 실행 근거 사용 가능 여부
    // ------------------------------------------------------------

    /** 아직 정리하지 않았다 */
    public static final String PENDING = "PENDING";

    /** 정리를 마쳤고 이 실행이 현재 세대로 선택됐다 */
    public static final String READY = "READY";

    /**
     * 정리나 후보 저장에 실패했다.
     * 관측은 남아 있으므로 AI 를 다시 부르지 않고 정리만 재시도한다.
     */
    public static final String CANONICAL_FAILED = "FAILED";

    /**
     * 같은 조합에서 다른 실행이 먼저 현재 세대가 됐다.
     *
     * 늦게 끝난 실행이 이미 선택된 세대를 밀어내면
     * 실행 근거가 도중에 바뀐다. 그래서 먼저 잡은 쪽을 유지한다.
     */
    public static final String NOT_SELECTED = "NOT_SELECTED";

    // ------------------------------------------------------------

    private Integer runNo;

    private Integer sourceBenefitNo;
    private String  promptKey;
    private Integer promptVersion;
    private String  sourceTextHash;
    private String  modelName;

    private String extractionStatus;
    private String canonicalStatus;

    /**
     * 이 정책 · 이 세대에서 지금 실행에 쓰는 실행이면 'Y', 아니면 비어 있다.
     *
     * 값이 비어 있는 행은 여러 개일 수 있지만 'Y' 인 행은 하나뿐이다.
     * MySQL 이 UNIQUE 에서 빈 값 중복을 허용하므로
     * uk_run_current 하나로 그것이 강제된다.
     * 두 실행이 동시에 현재를 주장하면 뒤엣것이 UNIQUE 위반으로 실패한다.
     */
    private String isCurrent;

    private Integer relationsExtracted;
    private Integer observationsSaved;
    private Integer observationsFailed;
    private Integer candidatesWritten;

    private String failureReason;

    private Date startedAt;
    private Date completedAt;
}
