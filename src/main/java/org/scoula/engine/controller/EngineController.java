package org.scoula.engine.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.engine.dto.EngineResultDto;
import org.scoula.engine.service.EngineService;
import org.scoula.security.account.domain.CustomUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/engine")
@RequiredArgsConstructor
public class EngineController {

    private final EngineService engineService;

    /**
     * 회원번호를 주소에서 받지 않는다.
     * JwtAuthenticationFilter가 SecurityContext에 넣어둔 CustomUser에서 꺼낸다.
     */
    @GetMapping("/benefits")
    public ResponseEntity<EngineResultDto> findEligibleBenefits(
            @AuthenticationPrincipal CustomUser user) {

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        EngineResultDto result = engineService.findEligibleBenefits(user.getMember().getMemberNo());
        return ResponseEntity.ok(result);
    }
}
