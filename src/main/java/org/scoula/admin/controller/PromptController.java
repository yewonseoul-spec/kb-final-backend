package org.scoula.admin.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.scoula.admin.domain.AiPromptVO;
import org.scoula.admin.dto.ConflictAiItemDto;
import org.scoula.admin.service.ConflictAiService;
import org.scoula.admin.service.PromptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/prompt")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;
    private final ConflictAiService conflictAiService;

    @Data
    public static class SaveReq {
        private String promptKey;
        private String content;
        private String memo;
    }

    @Data
    public static class TestReq {
        private Integer benefitNo;
        private String content;
    }

    /** 등록된 프롬프트 키 목록 */
    @GetMapping("/keys")
    public ResponseEntity<List<String>> keys() {
        return ResponseEntity.ok(promptService.getKeys());
    }

    /** 한 키의 버전 이력. 최신 버전이 위로 온다 */
    @GetMapping("/{promptKey}/versions")
    public ResponseEntity<List<AiPromptVO>> versions(@PathVariable String promptKey) {
        return ResponseEntity.ok(promptService.getVersions(promptKey));
    }

    /** 새 버전 저장. 저장만 하고 적용하지는 않는다 */
    @PostMapping("/versions")
    public ResponseEntity<Integer> create(@RequestBody SaveReq req,
                                          @RequestParam(required = false) Integer memberNo) {
        int no = promptService.createVersion(
                req.getPromptKey(), req.getContent(), req.getMemo(), memberNo);
        return ResponseEntity.ok(no);
    }

    /** 이 버전을 사용중으로 전환한다 */
    @PostMapping("/{promptKey}/activate/{promptNo}")
    public ResponseEntity<Void> activate(@PathVariable String promptKey,
                                         @PathVariable int promptNo) {
        promptService.activate(promptKey, promptNo);
        return ResponseEntity.ok().build();
    }

    /**
     * 시험 실행.
     * 활성 버전을 바꾸기 전에 결과를 확인한다. DB 에 쓰지 않는다.
     */
    @PostMapping("/test")
    public ResponseEntity<ConflictAiItemDto> test(@RequestBody TestReq req) {
        return ResponseEntity.ok(
                conflictAiService.testPrompt(req.getBenefitNo(), req.getContent()));
    }

    /** 버전 삭제. 사용중이거나 마지막 하나면 거부된다 */
    @DeleteMapping("/{promptKey}/versions/{promptNo}")
    public ResponseEntity<Void> delete(@PathVariable String promptKey,
                                       @PathVariable int promptNo) {
        promptService.deleteVersion(promptKey, promptNo);
        return ResponseEntity.ok().build();
    }
}
