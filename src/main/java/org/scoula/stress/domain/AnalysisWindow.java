package org.scoula.stress.domain;

import lombok.Getter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 소비 분석 기간
 * 당월은 제외한다. 아직 온전한 한 달이 아니고, 포함하면 월초에 실행할 때와 월말에 실행할 때
 * 결과가 달라져 같은 사용자에게 같은 답을 줄 수 없기 때문이다.
 * @fileName        : AnalysisWindow
 * @author          : 박상호
 * @since           : 2026-08-11
 */
@Getter
public class AnalysisWindow {

    /** 한국 서비스이므로 서버 기본 시간대에 맡기지 않는다 */
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** 최대 분석 개월 수 */
    public static final int MAX_MONTHS = 6;

    /** 분석 대상 완결월 목록. 과거에서 최근 순 */
    private final List<YearMonth> months;

    private AnalysisWindow(List<YearMonth> months) {
        this.months = Collections.unmodifiableList(new ArrayList<>(months));
    }

    /**
     * 분석 창을 생성한다
     *
     * @param baseDate           기준일
     * @param firstSpendingMonth 소비 데이터가 처음 나타난 달. 없으면 null
     * @param skipFirstMonth     최초 거래월을 부분 월로 보고 제외할지 여부
     */
    public static AnalysisWindow createWindow(LocalDate baseDate,
                                              YearMonth firstSpendingMonth,
                                              boolean skipFirstMonth) {

        if (baseDate == null) {
            throw new IllegalArgumentException("기준일이 없습니다");
        }

        YearMonth endMonth = YearMonth.from(baseDate).minusMonths(1);
        YearMonth startMonth = endMonth.minusMonths(MAX_MONTHS - 1L);

        // 데이터 수집 시작 이전은 0 원이 아니라 모르는 기간이므로 창에서 뺀다
        if (firstSpendingMonth != null) {
            YearMonth coverageStart = skipFirstMonth
                    ? firstSpendingMonth.plusMonths(1)
                    : firstSpendingMonth;
            if (coverageStart.isAfter(startMonth)) {
                startMonth = coverageStart;
            }
        }

        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = startMonth; !month.isAfter(endMonth); month = month.plusMonths(1)) {
            months.add(month);
        }
        return new AnalysisWindow(months);
    }

    /**
     * 오늘을 기준으로 최초 거래월을 제외하는 기본 규칙으로 생성한다
     */
    public static AnalysisWindow createWindow(YearMonth firstSpendingMonth) {
        return createWindow(LocalDate.now(SERVICE_ZONE), firstSpendingMonth, true);
    }

    /**
     * 정상 관측된 완결월 수를 반환한다. 거래가 있었던 달의 수가 아니다
     */
    public int getObservationMonths() {
        return months.size();
    }

    public boolean isEmpty() {
        return months.isEmpty();
    }

    public YearMonth getStartMonth() {
        return months.isEmpty() ? null : months.get(0);
    }

    public YearMonth getEndMonth() {
        return months.isEmpty() ? null : months.get(months.size() - 1);
    }

    /**
     * 조회 시작일을 반환한다. 이 날짜를 포함한다
     */
    public LocalDate getStartDateInclusive() {
        return months.isEmpty() ? null : getStartMonth().atDay(1);
    }

    /**
     * 조회 종료일을 반환한다. 반열린 구간이므로 이 날짜는 포함하지 않는다
     */
    public LocalDate getEndDateExclusive() {
        return months.isEmpty() ? null : getEndMonth().plusMonths(1).atDay(1);
    }
}
