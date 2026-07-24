package org.scoula.engine.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.engine.dto.EngineResultDto;
import org.scoula.engine.service.EngineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/engine")
@RequiredArgsConstructor
public class EngineController {

    private final EngineService engineService;

    @GetMapping("/benefits/{memberNo}")
    public ResponseEntity<EngineResultDto> findEligibleBenefits(
            @PathVariable int memberNo) {
        EngineResultDto result = engineService.findEligibleBenefits(memberNo);
        return ResponseEntity.ok(result);
    }
}
