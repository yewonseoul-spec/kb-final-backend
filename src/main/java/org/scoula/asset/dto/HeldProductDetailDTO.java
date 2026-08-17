package org.scoula.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeldProductDetailDTO {
    private Integer productNo;
    private String productName; // 상품명
    private String orgName; // 은행 이름
    private String productType; // 상품 종류
    private long holdAmount; // 보유 금액
    private Double interestRate; // 연이율
    private String maturityDate; // 만기일
}
