package org.scoula.stress.domain;

/**
 * 입력 데이터의 상태
 * 전체 응답을 성공과 실패 하나로 끝내지 않고 항목별로 상태를 내려준다.
 * 소비는 있고 소득은 모르고 잔액은 아는 사용자가 있을 수 있기 때문이다.
 * @fileName        : DataStatus
 * @author          : 박상호
 * @since           : 2026-08-12
 */
public enum DataStatus {

    /** 값을 안다 */
    KNOWN,

    /** 값을 모른다. 0 이라는 뜻이 아니다 */
    UNKNOWN,

    /** 관측 완결월이 없어 계산할 수 없다 */
    INSUFFICIENT_HISTORY,

    /** 등록된 계좌가 없다 */
    NO_ACCOUNT,

    /** 사용자가 가정을 선택해야 계산할 수 있다 */
    NEEDS_USER_ASSUMPTION
}