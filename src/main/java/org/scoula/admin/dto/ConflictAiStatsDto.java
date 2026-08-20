package org.scoula.admin.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * dry-run 집계.
 *
 * 지금까지 설계 논의는 전부 이 숫자가 없는 상태에서 세운 가설이다.
 * 특히 combinationApplicability 의 UNKNOWN 비율이 gate 설계 전체를 좌우한다.
 */
@Data
public class ConflictAiStatsDto {

    private int total;
    private int failed;

    private Map<String, Integer> scope            = new LinkedHashMap<>();
    private Map<String, Integer> relation         = new LinkedHashMap<>();
    private Map<String, Integer> direction        = new LinkedHashMap<>();
    private Map<String, Integer> timing           = new LinkedHashMap<>();
    private Map<String, Integer> subject          = new LinkedHashMap<>();
    private Map<String, Integer> restrictionStage = new LinkedHashMap<>();
    private Map<String, Integer> combinationApplicability = new LinkedHashMap<>();
    private Map<String, Integer> conditionType    = new LinkedHashMap<>();

    /** 범주 표현 실제 목록. categoryCode enum 을 여기서 도출한다 */
    private Map<String, Integer> rawCategory      = new TreeMap<>();

    private int relationCount;
    private int namedTargetCount;
    private int categoryOnlyCount;

    /** targetName 이 evidence 안에 canonical 로 실제 존재하는 건수 */
    private int evidenceVerified;
    private int evidenceFailed;

    /** gate 조건별 누적 통과율. 어디서 급락하는지가 병목이다 */
    private Map<String, Integer> gateFunnel       = new LinkedHashMap<>();

    public void bump(Map<String, Integer> map, String key) {
        String k = (key == null || key.trim().isEmpty()) ? "(none)" : key.trim();
        map.merge(k, 1, Integer::sum);
    }
}
