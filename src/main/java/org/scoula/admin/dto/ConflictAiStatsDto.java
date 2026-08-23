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

    /**
     * Candidate 저장 계측.
     *
     * INSERT IGNORE 는 UNIQUE 충돌에서 예외 없이 0 을 돌려준다.
     * 그래서 저장되지 못한 건이 아무 흔적 없이 사라지고,
     * AI 가 관계를 못 뽑은 것인지 저장에서 사라진 것인지 구분할 수 없었다.
     *
     * relationCount 와 candidateAttempted 는 다른 계층이다.
     * 앞은 AI 출력 집계고, 뒤는 실제 저장을 시도한 건수다.
     * scope 가 OTHER_POLICY 가 아니면 저장을 시도하지 않으므로 둘은 같지 않다.
     *
     * attempted = inserted + ignored + failed 는 항상 성립해야 한다.
     */
    private int candidateAttempted;
    private int candidateInserted;
    private int candidateIgnored;
    private int candidateFailed;

    /** targetName 이 evidence 안에 canonical 로 실제 존재하는 건수 */
    private int evidenceVerified;
    private int evidenceFailed;

    /**
     * 병행 기록 계측.
     *
     * 실행 기록 실패와 관측 저장 실패를 따로 센다.
     * 하나로 합치면 "실행 자체를 못 만든 것" 과
     * "실행은 만들었는데 관측 일부가 안 들어간 것" 이 구분되지 않는다.
     * 앞은 그 정책의 기록이 통째로 없는 것이고, 뒤는 근거가 일부 빠진 것이라
     * 나중에 무엇을 다시 해야 하는지가 다르다.
     *
     * 실행 기록을 만들지 못하면 관측을 붙일 곳이 없으므로
     * 그 정책의 관측 저장은 아예 시도하지 않는다.
     * 따라서 shadowRunsFailed 가 늘어난 정책은
     * observationsAttempted 에 한 건도 잡히지 않는다.
     *
     * shadowRunsAttempted   = shadowRunsCreated  + shadowRunsFailed
     * observationsAttempted = observationsInserted + observationsFailed
     */
    private int shadowRunsAttempted;
    private int shadowRunsCreated;
    private int shadowRunsFailed;

    /**
     * 실행 기록은 만들었는데 마지막 상태를 남기지 못한 건수.
     *
     * 실행을 못 만든 것과는 다른 실패다.
     * 앞은 그 정책의 기록이 아예 없는 것이고,
     * 뒤는 기록은 있는데 어디까지 갔는지가 안 적힌 것이다.
     * 뒤쪽은 DB 에 시작 상태로 남아 있어 나중에 사람이 찾아봐야 한다.
     *
     * 그래서 shadowRunsFailed 로 세지 않는다.
     * 그렇게 세면 실행을 못 만들었다는 뜻이 되어 실제 DB 상태와 어긋나고,
     * 위의 shadowRunsAttempted = Created + Failed 도 깨진다.
     *
     * 이 값이 없으면 마지막 상태 기록이 실패해도
     * 응답에는 모든 숫자가 성공으로 보인다.
     * 콘솔 로그에만 남고 화면에서는 알 수 없는 상태가 된다.
     *
     * shadowRunsUnfinalized <= shadowRunsCreated
     */
    private int shadowRunsUnfinalized;

    private int observationsAttempted;
    private int observationsInserted;
    private int observationsFailed;

    /** gate 조건별 누적 통과율. 어디서 급락하는지가 병목이다 */
    private Map<String, Integer> gateFunnel       = new LinkedHashMap<>();

    public void bump(Map<String, Integer> map, String key) {
        String k = (key == null || key.trim().isEmpty()) ? "(none)" : key.trim();
        map.merge(k, 1, Integer::sum);
    }
}
