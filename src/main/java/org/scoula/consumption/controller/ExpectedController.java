package org.scoula.consumption.controller;

import org.scoula.consumption.dto.ExpectedReqDTO;
import org.scoula.consumption.service.ConsumptionService;
import org.scoula.security.account.domain.CustomUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/consumption/expected")
public class ExpectedController {

    private final ConsumptionService consumptionService;

    public ExpectedController(ConsumptionService consumptionService) {
        this.consumptionService = consumptionService;
    }

    // 예상 소비 추가
    @PostMapping
    public void addExpected(@RequestBody ExpectedReqDTO request, @AuthenticationPrincipal CustomUser user) {
        Integer memberNo = user.getMember().getMemberNo();

        consumptionService.addExpected(memberNo, request);
    }

    // 예상 소비 수정
    @PutMapping("/{expectedNo}")
    public void updateExpected(
            @PathVariable("expectedNo") Long expectedNo,
            @RequestBody ExpectedReqDTO request
    ) {
        consumptionService.updateExpected(expectedNo, request);
    }

    // 예상 소비 삭제
    @DeleteMapping("{expectedNo}")
    public void deleteExpected(@PathVariable("expectedNo") Long expectedNo) {
        consumptionService.deleteExpected(expectedNo);
    }
}
