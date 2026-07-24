package org.scoula.engine.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.engine.dto.BenefitResDto;
import org.scoula.engine.service.EngineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/engine")
@RequiredArgsConstructor
public class EngineController {

    private final EngineService engineService;

        //자격조건 필터링된 정책 목록 조회
    @GetMapping("/benefits/{memberNo}")
    public ResponseEntity<List<BenefitResDto>> findEligibleBenefits(
            @PathVariable int memberNo){
        List<BenefitResDto> benefits =
                engineService.findEligibleBenefits(memberNo);

        return ResponseEntity.ok(benefits);
    }

}
