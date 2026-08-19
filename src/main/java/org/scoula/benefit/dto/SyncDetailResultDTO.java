package org.scoula.benefit.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 동기화 결과.
 * 기존 메서드는 처리 건수만 돌려줘서 어떤 혜택이 갱신됐는지 알 수 없었다.
 * 관리자 화면에서 갱신 내역을 보여주기 위해 처리한 혜택 목록을 함께 반환한다.
 */
@Data
public class SyncDetailResultDTO {
    private int totalCount;
    private int deleteCount;
    private List<SyncedBenefitDTO> items = new ArrayList<>();

    public void add(Integer benefitNo, String actionType, String changedSummary) {
        totalCount++;
        if (benefitNo != null) {
            items.add(new SyncedBenefitDTO(benefitNo, actionType, changedSummary));
        }
    }

    public void addDeleted(Integer benefitNo, String changedSummary) {
        deleteCount++;
        add(benefitNo, "D", changedSummary);
    }
}
