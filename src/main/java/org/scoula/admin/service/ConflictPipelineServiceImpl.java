package org.scoula.admin.service;

import lombok.RequiredArgsConstructor;
import org.scoula.admin.constant.ConflictPromptKeys;
import org.scoula.admin.dto.ConflictAiRunDto;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 번의 실행은 한 세대로만 돈다.
 *
 * 세대를 정하는 일을 여기서 한 번만 하고
 * 아래 단계들에는 그 값을 넘긴다.
 * 각 단계가 스스로 사용중인 세대를 다시 조회하면
 * 실행 도중 세대가 바뀌었을 때 단계마다 다른 세대를 보게 된다.
 *
 * @author 박상호
 * @since 2026-08-21
 */
@Service
@RequiredArgsConstructor
public class ConflictPipelineServiceImpl implements ConflictPipelineService {

    private final ConflictAiService conflictAiService;
    private final ConflictGateServiceImpl conflictGateService;
    private final PromptService promptService;

    @Override
    public Map<String, Object> runAll() {

        long started = System.currentTimeMillis();

        // 시작할 때 한 번 정하고 끝까지 이 값을 쓴다
        Integer version = promptService.requireActiveVersion(
                ConflictPromptKeys.CONFLICT_DETECTION);
        System.out.println("[전구간] 프롬프트 v" + version + " 로 실행합니다");

        ConflictAiRunDto run = conflictAiService.runAndSaveVersion(version, 0, 0);
        Map<String, Integer> cross = conflictGateService.crossCheckVersion(version);
        List<Map<String, Object>> gate = conflictGateService.applyGateVersion(version);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("promptVersion", version);
        out.put("candidateTotal", run.getCandidateTotal());
        out.put("analyzed", run.getStats() == null ? 0 : run.getStats().getTotal());
        out.put("candidateInserted", run.getStats() == null ? 0 : run.getStats().getCandidateInserted());
        out.put("candidateIgnored", run.getStats() == null ? 0 : run.getStats().getCandidateIgnored());
        out.put("candidateFailed", run.getStats() == null ? 0 : run.getStats().getCandidateFailed());
        out.put("crossCheck", cross);
        out.put("gate", gate);
        out.put("durationMs", System.currentTimeMillis() - started);
        return out;
    }
}
