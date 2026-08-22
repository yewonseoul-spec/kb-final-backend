package org.scoula.admin.domain;

import lombok.Data;

import java.util.Date;

/**
 * AI 가 공고문에서 뽑아 반환한 관계 하나. 손대지 않은 원본이다.
 *
 * 후보와 역할이 다르다.
 *
 *     관측   AI 가 무엇이라고 말했는가
 *     후보   그 말들을 놓고 시스템이 실행 근거로 삼을 수 있는 사실은 무엇인가
 *
 * 그래서 여기에는 판정 결과를 담는 자리가 없다.
 * 상호 대조 결과나 검수 상태, 관리자 판정은 전부 후보가 갖는다.
 * 두 가지를 한 곳에 담으면 AI 가 한 말과 우리가 내린 판단을 구분할 수 없게 된다.
 *
 * 상대 정책 번호도 여기 두지 않는다.
 * 그것은 이름을 DB 에서 찾아본 결과이지 공고문에 적힌 내용이 아니다.
 *
 * 같은 의미의 관측이 한 실행 안에 여러 건 들어올 수 있다.
 * AI 가 같은 상대를 표기만 다르게 두 번 말하는 일이 실제로 있고,
 * 그때 두 줄의 세부 값이 서로 다를 수 있다.
 * 그 사실을 보려면 전부 저장돼 있어야 하므로 의미 기반 중복 제한을 걸지 않는다.
 *
 * @author 박상호
 * @since 2026-08-21
 */
@Data
public class ConflictObservationVO {

    private Integer observationNo;

    private Integer runNo;

    /**
     * AI 응답 안에서의 순번.
     *
     * 같은 실행 안에서만 유일하다.
     * 이 값과 실행 번호를 묶어 중복을 막는 이유는
     * 같은 응답을 두 번 저장하는 사고만 막기 위해서다.
     * 의미가 같은 관측 여러 건은 서로 다른 순번으로 전부 저장된다.
     */
    private Integer observationIndex;

    private Integer sourceBenefitNo;
    private String  scope;
    private String  relation;

    private String targetNameRaw;
    private String targetCategoryRaw;

    private String direction;
    private String timing;
    private String subjectScope;
    private String restrictionStage;
    private String combinationApplicability;
    private String triggerScope;

    /** AI 가 말한 값 그대로. 정리 단계에서 붙이는 값은 여기 들어오지 않는다 */
    private String conditionType;
    private String conditionText;

    private String  evidenceText;
    private String  evidenceVerified;
    private Double  confidence;

    /**
     * 어느 관측끼리 하나로 정리할지 묶는 키.
     *
     * 후보 쪽 같은 이름의 컬럼과 값은 같지만 역할이 다르다.
     * 후보에서는 중복을 막는 데 쓰고 여기서는 묶는 데만 쓴다.
     * 여기에 중복 제한을 걸면 이 테이블을 만든 이유가 사라진다.
     */
    private String dedupeKey;

    private Date createdAt;
}
