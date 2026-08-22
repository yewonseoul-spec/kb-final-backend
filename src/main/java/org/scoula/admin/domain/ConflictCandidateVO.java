package org.scoula.admin.domain;

import lombok.Data;

@Data
public class ConflictCandidateVO {

    private Integer candidateNo;
    private Integer sourceBenefitNo;

    private String scope;
    private String relation;

    private String targetNameRaw;
    private String targetCategoryRaw;
    private String categoryCode;

    private String direction;
    private String timing;
    private String subjectScope;
    private String restrictionStage;
    private String combinationApplicability;
    private String triggerScope;

    private String conditionType;
    private String conditionText;

    private String  evidenceText;
    private String  evidenceVerified;
    private Double  confidence;

    private Integer mappedBenefitNo;
    private String  resolverResult;
    private String  resolverCandidates;
    private Integer resolveRetryCnt;

    private String verifierVerdict;
    private String blockingReasons;
    private String crosscheckResult;

    private String analysisStatus;
    private String workflowStatus;
    private String enforcementState;
    private String reviewReason;

    /** 관리자가 네 버튼 중 무엇을 눌렀는가. BLOCK / PARTIAL / NOT_CONFLICT */
    private String conflictDecision;

    private String discardReason;

    private String  modelName;
    private String  promptKey;
    private Integer promptVersion;
    private String  sourceTextHash;
    private String  dedupeKey;

    /**
     * 이 후보를 만든 분석 실행.
     *
     * 비어 있으면 실행 기록을 남기기 전 방식으로 저장된 행이다.
     * 기존 행에는 이 값이 없으므로 조회에서 이 값을 반드시 요구하면
     * 지금까지 쌓인 검수 결과가 통째로 사라진다.
     */
    private Integer canonicalRunNo;

    /**
     * 자동 판단에 쓰이는 값에서 관측이 갈렸는가. STABLE / CONFLICTING
     *
     * 갈렸다고 해서 자동 차단이 열리지는 않는다.
     * 갈린 값은 이미 모르겠다는 값으로 모이기 때문이다.
     * 이 값은 관리자에게 이유를 설명하고, 나중에 사람이 내린 판단을
     * 새 세대로 승계할지 정할 때 쓴다.
     */
    private String reconciliationStatus;

    /** 몇 건의 관측을 정리한 결과인가 */
    private Integer observationCount;
}