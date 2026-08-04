package org.scoula.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountDTO {
    private Integer accountId;
    private String bankName; // 은행명
    private String accountNo; // 계좌번호
    private Long balance; // 잔액
}
