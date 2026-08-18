package org.scoula.consumption.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.scoula.consumption.mapper.ConsumptionMapper;
import org.scoula.consumption.service.ConsumptionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class RecurringSpendingScheduler {

    private final ConsumptionService consumptionService;
    private final ConsumptionMapper consumptionMapper;

    public RecurringSpendingScheduler(ConsumptionService consumptionService, ConsumptionMapper consumptionMapper) {
        this.consumptionService = consumptionService;
        this.consumptionMapper = consumptionMapper;
    }

    // 매일 새벽 3시에 한 번씩 실행
    @Scheduled(cron = "0 0 3 * * *")
    public void detectAllMembersRecurringSpending() {
        List<Integer> allMemberNos = consumptionMapper.selectAllMemberNos();

        log.info("정기 지출 자동 감지 스케줄 시작 - 대상 회원 수: {}", allMemberNos.size());

        for (Integer memberNo : allMemberNos) {
            try {
                consumptionService.detectRecurringSpending(memberNo);
            } catch (Exception e) {
                log.error("회원 {} 정기 지출 감지 실패: {}", memberNo, e.getMessage());
            }
        }

        log.info("정기 지출 자동 감지 스케줄 완료");
    }
}
