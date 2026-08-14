package org.scoula.stress.domain;

/**
 * 스트레스 평가 기간
 * 선택한 충격이 이 기간 동안 같은 수준으로 지속된다고 가정한다.
 * 따라서 단순한 점수 목표선이 아니라 반복 충격을 몇 번 누적할지 정하는 기간이며,
 * 일회성 충격과 반복 충격을 같은 원 단위로 합치는 기준이기도 하다.
 * 안전 여부를 판정하는 임계값이 아니다.
 */
public final class StressHorizon {

    /** 평가 기간. 개월 */
    public static final int MONTHS = 6;

    private StressHorizon() {
    }
}