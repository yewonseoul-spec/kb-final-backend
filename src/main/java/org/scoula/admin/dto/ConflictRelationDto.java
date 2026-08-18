package org.scoula.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 정책 하나가 지목한 상대와의 관계 하나.
 *
 * 한 문장 안에 이름 지목과 범주 지목이 함께 나오거나
 * 금지와 허용이 같이 나오는 경우가 실제로 있어서 배열로 받는다.
 * (예: "기쁨두배통장 등 유사자산형성사업과 중복 신청 불가")
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConflictRelationDto {

    /** FORBIDDEN / CONDITIONAL / ALLOWED */
    private String relation;

    /** 본문에 적힌 상대 정책명 그대로. 정식 명칭으로 고치지 않는다 */
    private String targetName = "";

    /** 이름 없이 범주로만 적혔을 때 그 표현 그대로 */
    private String targetCategory = "";

    /** BIDIRECTIONAL / SOURCE_TO_TARGET / UNKNOWN */
    private String direction = "UNKNOWN";

    /** CURRENT / PAST / CURRENT_OR_PAST / UNKNOWN */
    private String timing = "UNKNOWN";

    /** APPLICANT / HOUSEHOLD / UNKNOWN */
    private String subject = "UNKNOWN";

    /** APPLICATION / BENEFIT_RECEIPT / SELECTION / HISTORY / UNKNOWN */
    private String restrictionStage = "UNKNOWN";

    /**
     * YES / NO / UNKNOWN
     * 아직 둘 다 받지 않은 신규 추천 조합에도 이 관계를 적용할 근거가
     * 원문에 있는가. CURRENT라는 이유만으로 YES가 되지 않는다.
     */
    private String combinationApplicability = "UNKNOWN";

    /** AMOUNT_ADJUSTMENT / HISTORY_CONDITION / HOUSEHOLD_CONDITION / ELIGIBILITY_CONDITION */
    private String conditionType = "";

    private String conditionText = "";

    /** 이 관계의 근거가 된 본문 문장. 요약하지 않고 그대로 */
    private String evidence = "";

    private Double confidence = 0.0;
}