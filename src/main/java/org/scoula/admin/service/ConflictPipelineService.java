package org.scoula.admin.service;

import java.util.Map;

/**
 * 분석부터 판정까지를 한 번에 실행한다.
 *
 * 세 단계를 따로 부르면 각 단계가 그때그때 사용중인 프롬프트 세대를 다시 조회한다.
 * 실행이 몇 분 걸리는 동안 관리자가 다른 세대를 적용하면
 * 저장은 이전 세대로 되고 대조와 판정은 새 세대로 도는 상태가 될 수 있다.
 * 그러면 방금 저장한 결과가 판정 대상에서 빠진다.
 *
 * 그래서 시작할 때 세대를 한 번 정하고 끝까지 그 세대로 돈다.
 *
 * @author 박상호
 * @since 2026-08-21
 */
public interface ConflictPipelineService {

    /**
     * 사용중인 세대로 분석 · 대조 · 판정을 순서대로 실행한다.
     * 실행 중 사용중인 세대가 바뀌어도 이 실행은 시작할 때 정한 세대를 유지한다.
     */
    Map<String, Object> runAll();
}
