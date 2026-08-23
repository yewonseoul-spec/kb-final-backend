package org.scoula.admin.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.scoula.admin.domain.AiPromptVO;
import org.scoula.admin.dto.ConflictAiItemDto;
import org.scoula.admin.service.ConflictAiService;
import org.scoula.admin.service.PromptService;
import org.scoula.consumption.dto.ConsumptionPromptTestDTO;
import org.scoula.consumption.service.AiAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/prompt")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;
    private final ConflictAiService conflictAiService;

    // [상호 추가] 소비 분석 프롬프트 시험 실행용
    private final AiAnalysisService aiAnalysisService;

    @Data
    public static class SaveReq {
        private String promptKey;
        private String content;
        private String memo;
    }

    /**
     * 중복수혜 분석 시험 실행 요청.
     *
     * [상호 수정] Integer benefitNo → String plcyNo.
     *            benefit_no 는 DB 를 다시 적재하면 재배정되어
     *            화면에 적어둔 예시 번호가 엉뚱한 정책을 가리키게 된다.
     *            plcy_no 는 온통청년이 부여한 20자리 숫자 문자열이라 그대로 남는다.
     */
    @Data
    public static class TestReq {
        private String plcyNo;
        private String content;
    }

    /**
     * [상호 추가] 소비 분석 시험 실행 요청.
     *
     * summaryJson 은 관리자가 직접 넣는 목데이터다.
     * 회원번호로 조회하지 않는 이유는, 회원 소비 내역이 매일 바뀌면
     * 프롬프트를 고쳤을 때 결과가 달라진 원인이 프롬프트인지 데이터인지 알 수 없기 때문이다.
     */
    @Data
    public static class ConsumptionTestReq {
        private String summaryJson;
        private String analysisPrompt;
        private String verificationPrompt;
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
     * 중복수혜 분석 시험 실행.
     * 활성 버전을 바꾸기 전에 결과를 확인한다. DB 에 쓰지 않는다.
     */
    @PostMapping("/test")
    public ResponseEntity<ConflictAiItemDto> test(@RequestBody TestReq req) {
        return ResponseEntity.ok(
                conflictAiService.testPrompt(req.getPlcyNo(), req.getContent()));
    }

    /**
     * [상호 추가] 소비 분석 시험 실행.
     *
     * 분석을 돌리고 그 결과를 검증까지 태워 둘 다 돌려준다.
     * 저장하지 않은 프롬프트로도 실행되며 DB 에 쓰지 않는다.
     *
     * ★ 규칙 검증과 AI 검증 결과를 모두 담는다.
     *   AI 검증은 참고용이라 실패해도 결과가 사용자에게 나가므로,
     *   둘을 구분해 보여주지 않으면 관리자가 "통과"를 잘못 읽는다.
     */
    @PostMapping("/test/consumption")
    public ResponseEntity<ConsumptionPromptTestDTO> testConsumption(
            @RequestBody ConsumptionTestReq req) {
        return ResponseEntity.ok(
                aiAnalysisService.testAnalyze(
                        req.getSummaryJson(),
                        req.getAnalysisPrompt(),
                        req.getVerificationPrompt()));
    }

    /** 버전 삭제. 사용중이거나 마지막 하나면 거부된다 */
    @DeleteMapping("/{promptKey}/versions/{promptNo}")
    public ResponseEntity<Void> delete(@PathVariable String promptKey,
                                       @PathVariable int promptNo) {
        promptService.deleteVersion(promptKey, promptNo);
        return ResponseEntity.ok().build();
    }
}