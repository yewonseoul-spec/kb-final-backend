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
}