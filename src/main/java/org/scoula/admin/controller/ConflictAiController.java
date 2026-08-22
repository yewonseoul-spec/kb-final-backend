package org.scoula.admin.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.admin.dto.ConflictAiRunDto;
import org.scoula.admin.service.ConflictAiService;
import org.scoula.admin.service.ConflictGateService;
import org.scoula.admin.service.ConflictPipelineService;
import org.scoula.admin.service.ConflictReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/conflict")
@RequiredArgsConstructor
public class ConflictAiController {

    private final ConflictAiService conflictAiService;
    private final ConflictGateService conflictGateService;
    private final ConflictReviewService conflictReviewService;
    private final ConflictPipelineService conflictPipelineService;

    // ------------------------------------------------------------
    // 분석
    // ------------------------------------------------------------

    @GetMapping("/ai/dry-run")
    public ResponseEntity<ConflictAiRunDto> dryRun(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok(conflictAiService.dryRun(offset, limit));
    }

    @PostMapping("/ai/run")
    public ResponseEntity<ConflictAiRunDto> run(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(conflictAiService.runAndSave(offset, limit));
    }

    /**
     * 후보는 저장하지 않고 AI 가 말한 관계만 기록한다.
     *
     * 저장 경로를 바꾸기 전에 기록 쪽이 실제로 도는지 확인하는 통로다.
     * 기본값을 작게 둔 이유는 이 경로가 확인용이기 때문이다.
     * 대조나 판정, 규칙 생성으로 이어지지 않는다.
     */
    @PostMapping("/ai/shadow-run")
    public ResponseEntity<ConflictAiRunDto> shadowRun(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(conflictAiService.shadowRun(offset, limit));
    }

    // ------------------------------------------------------------
    // Cross-check / Gate
    // ------------------------------------------------------------

    @PostMapping("/gate/crosscheck")
    public ResponseEntity<Map<String, Integer>> crossCheck() {
        return ResponseEntity.ok(conflictGateService.crossCheck());
    }

    @PostMapping("/gate/apply")
    public ResponseEntity<List<Map<String, Object>>> applyGate() {
        return ResponseEntity.ok(conflictGateService.applyGate());
    }

    /** 동기화 후 미해소 건 재시도. 새 정책이 들어와 상대가 특정되면 검수로 올라온다 */
    @PostMapping("/gate/re-resolve")
    public ResponseEntity<Map<String, Integer>> reResolve() {
        return ResponseEntity.ok(conflictGateService.reResolve());
    }

    // ------------------------------------------------------------
    // 검수
    // ------------------------------------------------------------

    @GetMapping("/review/queue")
    public ResponseEntity<List<Map<String, Object>>> queue() {
        return ResponseEntity.ok(conflictReviewService.queue());
    }

    /**
     * 보류 중인 건. 만료 전에도 다시 볼 수 있어야 한다.
     * 고정 경로는 {candidateNo} 보다 먼저 선언해야 숫자로 해석되지 않는다.
     */
    @GetMapping("/review/deferred")
    public ResponseEntity<List<Map<String, Object>>> deferredQueue() {
        return ResponseEntity.ok(conflictReviewService.deferredQueue());
    }

    @GetMapping("/review/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(conflictReviewService.summary());
    }

    /** 확정분을 엔진이 읽는 Rule 로 내린다 */
    @PostMapping("/review/publish")
    public ResponseEntity<Map<String, Integer>> publish() {
        return ResponseEntity.ok(conflictReviewService.publishRules());
    }

    @GetMapping("/review/{candidateNo}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable int candidateNo) {
        return ResponseEntity.ok(conflictReviewService.detail(candidateNo));
    }

    /** decision — BLOCK / PARTIAL / NOT_CONFLICT */
    @PostMapping("/review/{candidateNo}/decide")
    public ResponseEntity<Void> decide(
            @PathVariable int candidateNo,
            @RequestParam String decision,
            @RequestParam(required = false) Integer mappedNo,
            @RequestParam(required = false) String reason,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.scoula.security.account.domain.CustomUser user) {
        // 판정자는 화면이 보내는 값이 아니라 토큰에서 채운다.
        Integer memberNo = (user == null) ? null : user.getMember().getMemberNo();
        conflictReviewService.decide(candidateNo, decision, mappedNo, reason, memberNo);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/review/{candidateNo}/defer")
    public ResponseEntity<Void> defer(
            @PathVariable int candidateNo,
            @RequestParam(defaultValue = "7") int days,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.scoula.security.account.domain.CustomUser user) {
        Integer memberNo = (user == null) ? null : user.getMember().getMemberNo();
        conflictReviewService.defer(candidateNo, days, memberNo);
        return ResponseEntity.ok().build();
    }
    /**
     * 분석 전 구간 실행.
     *
     * 지금까지는 분석 · 대조 · 판정을 각각 API 로 불러야 했다.
     * 운영자가 화면에서 실행할 수 없고 순서도 직접 지켜야 해서
     * 시연 환경 담당자에게 그대로 넘길 수 없었다.
     *
     * 정책 수에 따라 몇 분이 걸리므로 화면에서 대기 시간을 안내한다.
     */
    @PostMapping("/ai/run-all")
    public ResponseEntity<Map<String, Object>> runAll() {
        // 세 단계를 여기서 따로 부르면 각 단계가 사용중인 세대를 다시 조회한다.
        // 실행 도중 다른 세대가 적용되면 단계마다 다른 세대를 보게 되므로
        // 세대를 한 번만 정하는 일은 서비스 쪽에서 한다.
        return ResponseEntity.ok(conflictPipelineService.runAll());
    }}