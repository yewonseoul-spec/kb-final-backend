package org.scoula.stress.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 충격이 적용되는 대상 카테고리
 * 생활물가 충격을 전체 지출이 아니라 생활밀접 지출에만 거는 이유는
 * 가계가 체감하는 물가가 소비자물가가 아니라 생활물가이기 때문이다.
 * 다만 이 묶음은 통계청 생활물가지수의 품목 바스켓과 동일하지 않으며
 * 생활물가 충격의 대리 지표로 사용하는 것이다.
 * @fileName        : ShockTarget
 * @author          : 박상호
 * @since           : 2026-08-12
 */
public final class ShockTarget {

    /** 생활물가 충격 대상 */
    public static final Set<String> LIVING_COST_CATEGORIES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "식비", "마트·편의점", "카페·간식", "생활", "기타", "교통", "쇼핑")));

    /** 주거비 충격 대상 */
    public static final Set<String> HOUSING_CATEGORIES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "주거·공과금")));

    private ShockTarget() {
    }

    /**
     * 생활물가 충격 대상인지 확인한다
     */
    public static boolean isLivingCost(String categoryName) {
        return LIVING_COST_CATEGORIES.contains(categoryName);
    }

    /**
     * 주거비 충격 대상인지 확인한다
     */
    public static boolean isHousing(String categoryName) {
        return HOUSING_CATEGORIES.contains(categoryName);
    }
}
