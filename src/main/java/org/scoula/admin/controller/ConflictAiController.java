package org.scoula.admin.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.admin.dto.ConflictAiRunDto;
import org.scoula.admin.service.ConflictAiService;
import org.scoula.admin.service.ConflictGateService;
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
}