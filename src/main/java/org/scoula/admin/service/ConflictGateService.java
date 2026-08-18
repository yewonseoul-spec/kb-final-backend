package org.scoula.admin.service;

import java.util.List;
import java.util.Map;

public interface ConflictGateService {

    /** 상대 정책 공고가 나를 되짚었는지 확인해 방향성을 보강한다 */
    Map<String, Integer> crossCheck();

    /** Candidate 를 자동 처리 / 데이터 대기 / 검수로 판정한다 */
    List<Map<String, Object>> applyGate();

    /** 동기화 후 미해소 건의 Resolver 재시도 */
    Map<String, Integer> reResolve();
}