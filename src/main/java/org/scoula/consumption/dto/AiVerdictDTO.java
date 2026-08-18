package org.scoula.consumption.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiVerdictDTO {
    private boolean passed; // 통과 여부
    private String reason; // 통과/실패 판단 이유
}
