package org.scoula.stress.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 스트레스 테스트 계산 요청
 * 세계와 강도의 정의는 화면이 가지고 있고 서버는 충격 값만 받는다.
 * 세계를 늘리거나 강도를 바꿀 때 DB 를 건드리지 않기 위해서다.
 * 회원번호는 본문 값을 쓰지 않고 컨트롤러가 토큰에서 꺼내 채운다.
 */
@Getter
@Setter
@NoArgsConstructor
public class StressResultReqDto {

    /** 회원번호. 토큰에서 채운다 */
    private int memberNo;

    /** 세계 코드. 화면 표시와 로그에만 쓴다 */
    private String worldCode;

    /** 선택한 강도 문구. 화면 표시에만 쓴다 */
    private String stageLabel;

    /** 생활밀접 지출 증가 비율. 0 이상 1 이하 */
    private BigDecimal expenseRate;

    /** 소득 감소 비율. 0 이상 1 이하 */
    private BigDecimal incomeRate;

    /** 정액 월 지출 증가액. 치료가 이어지는 경우처럼 카테고리와 무관한 증가 */
    private Long fixedExpense;

    /** 일회성 충격 금액 */
    private Long oneTimeAmount;

    /** 카테고리별 지출 조정. 사용자가 슬라이더로 고른 감소율 */
    private List<CategoryAdjustReqDto> adjustments;
}