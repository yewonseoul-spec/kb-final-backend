package org.scoula.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaturityDTO {
    private Integer linkNo; // 연결 번호
    private String productName; // 상품명
    private String orgName; // 금융기관명
    private String maturityDate; // 만기일
}
