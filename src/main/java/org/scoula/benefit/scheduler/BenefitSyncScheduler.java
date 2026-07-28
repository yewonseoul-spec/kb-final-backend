package org.scoula.benefit.scheduler;

import lombok.RequiredArgsConstructor;
import org.scoula.benefit.service.BenefitService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BenefitSyncScheduler {

    private final BenefitService benefitService;

    // 매일 오후 11시 실행
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void syncDailyYouthPolicies() {
        int count = benefitService.syncDailyYouthPolicies();

        System.out.println("[청년혜택 자동 동기화 완료] 처리 건수 = " + count);
    }
}