package org.scoula.admin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConflictAiRunDto {

    private int candidateTotal;

    /** 이번 구간의 시작 위치 */
    private int offset;

    /** 다음에 넣을 offset. 더 없으면 -1 */
    private int nextOffset;

    private long durationMs;
    private ConflictAiStatsDto stats;
    private List<ConflictAiItemDto> items = new ArrayList<>();
}