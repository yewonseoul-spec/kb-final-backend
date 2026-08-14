package org.scoula.consumption.controller;

import org.scoula.consumption.dto.CategoryAmountDTO;
import org.scoula.consumption.dto.MonthlyTotalDTO;
import org.scoula.consumption.service.AiAnalysisService;
import org.scoula.consumption.service.CategoryAmountService;
import org.scoula.consumption.service.MonthlyTrendService;
import org.scoula.consumption.service.SpendingSummaryService;
import org.scoula.security.account.domain.CustomUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.YearMonth;
import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/consumption/analysis")
public class AnalysisController {

    private final SpendingSummaryService spendingSummaryService;
    private final AiAnalysisService aiAnalysisService;
    private final CategoryAmountService categoryAmountService;
    private final MonthlyTrendService monthlyTrendService;

    public AnalysisController(SpendingSummaryService spendingSummaryService,
                              AiAnalysisService aiAnalysisService,
                              CategoryAmountService categoryAmountService,
                              MonthlyTrendService monthlyTrendService) {
        this.spendingSummaryService = spendingSummaryService;
        this.aiAnalysisService = aiAnalysisService;
        this.categoryAmountService = categoryAmountService;
        this.monthlyTrendService = monthlyTrendService;
    }

    // AI 소비 패턴 분석
    @GetMapping(value = "/ai", produces = "application/json;charset=UTF-8")
    public String getAiAnalysis(HttpServletRequest request, @AuthenticationPrincipal CustomUser user) {
        Integer memberNo = user.getMember().getMemberNo();
        String yearMonth = YearMonth.now().toString();

        String summaryText = spendingSummaryService.buildSummaryText(memberNo, yearMonth);
        return aiAnalysisService.analyze(memberNo, summaryText);
    }

    // 카테고리별 소비 금액 조회
    @GetMapping("/category")
    public List<CategoryAmountDTO> getCategoryAmounts(HttpServletRequest request, @AuthenticationPrincipal CustomUser user) {
        Integer memberNo = user.getMember().getMemberNo();
        String yearMonth = YearMonth.now().toString();

        return categoryAmountService.getCategoryAmounts(memberNo, yearMonth);
    }

    // 월별 소비 추이 조회
    @GetMapping("/monthly")
    public List<MonthlyTotalDTO> getMonthlyTrend(HttpServletRequest request, @AuthenticationPrincipal CustomUser user) {
        Integer memberNo = user.getMember().getMemberNo();
        return monthlyTrendService.getMonthlyTrend(memberNo);
    }
}
