package org.scoula.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 정책 하나에 대한 AI 분석 결과.
 *
 * scope 가 핵심이다.
 * SAME_POLICY 와 NOT_CONFLICT 는 규칙을 만들지 않고 버린다.
 * 본문에 "중복"이라는 말이 있어도 절반 가까이가 같은 사업 안에서의 중복이거나
 * 지원 범위 얘기라 여기서 걸러내지 못하면 잘못된 규칙이 만들어진다.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConflictAiItemDto {

    // ---------- AI 응답 ----------

    /** OTHER_POLICY / SAME_POLICY / NOT_CONFLICT / UNCERTAIN */
    private String scope;

    /** scope 가 OTHER_POLICY 일 때만 채워진다 */
    private List<ConflictRelationDto> relations = new ArrayList<>();

    /** scope 판단의 근거 문장 */
    private String scopeEvidence = "";

    // ---------- 우리가 채우는 것 ----------

    private Integer benefitNo;
    private String  plcyNm;
    private String  sprvsnInstCdNm;
    private String  isActive;
    private String  sourceTextHash;
    private String  modelName;
    private Integer promptVersion;

    /** 실패 시에만 채워진다. 실패를 '충돌 없음'으로 취급하지 않기 위해 분리한다 */
    private String errorMsg;
}