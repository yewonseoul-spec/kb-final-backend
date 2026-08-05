package org.scoula.stress.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.security.account.domain.CustomUser;
import org.scoula.stress.dto.StressResultReqDto;
import org.scoula.stress.dto.StressResultResDto;
import org.scoula.stress.dto.StressScenarioResDto;
import org.scoula.stress.service.StressService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stress")
@RequiredArgsConstructor
public class StressController {

    private final StressService stressService;

    /** stress-01: 스트레스 시나리오 목록 (시나리오 5종 × 충격 강도 3단계) */
    @GetMapping("/scenarios")
    public ResponseEntity<List<StressScenarioResDto>> findScenarios() {
        return ResponseEntity.ok(stressService.findScenarios());
    }

    /**
     * stress-02: 스트레스 테스트 계산
     *
     * 회원번호는 파라미터로 받지 않는다.
     * JwtAuthenticationFilter가 토큰을 검증하고 SecurityContext에 넣어둔
     * CustomUser에서 꺼내 쓴다. 주소창으로 남의 회원번호를 넣을 수 없다.
     *
     * 계산이 불가능한 경우에도 200으로 응답하고 status로 사유를 구분한다.
     * 프론트가 상황별로 다른 안내를 띄울 수 있어야 하기 때문이다.
     *   NO_SPENDING / NO_ACCOUNT / PROFILE_REQUIRED / SCENARIO_UNAVAILABLE
     */
    @PostMapping("/result")
    public ResponseEntity<StressResultResDto> calculate(
            @AuthenticationPrincipal CustomUser user,
            @RequestParam String scenarioCode,
            @RequestParam String shockLevel) {

        // 토큰이 없거나 만료된 경우. 프론트 응답 인터셉터가 401을 받아
        // 로그아웃 처리하고 로그인 화면으로 보낸다.
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        StressResultReqDto req = new StressResultReqDto();
        req.setMemberNo(user.getMember().getMemberNo());
        req.setScenarioCode(scenarioCode);
        req.setShockLevel(shockLevel);

        return ResponseEntity.ok(stressService.calculate(req));
    }
}