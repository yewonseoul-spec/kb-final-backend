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

    /** stress-01: 스트레스 시나리오 목록 */
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
     * 지출 조정은 배열이라 쿼리 파라미터로 받을 수 없어 본문으로 받는다.
     * 사용자가 직접 고른 감소율이며 시스템이 판단한 값이 아니다.
     *
     * 계산이 불가능한 경우에도 200으로 응답하고 상태 필드로 사유를 구분한다.
     * 소비는 있고 소득은 모르고 잔액은 아는 사용자가 있을 수 있으므로
     * 전체를 성공과 실패 하나로 끝내지 않는다.
     */
    @PostMapping("/result")
    public ResponseEntity<StressResultResDto> calculate(
            @AuthenticationPrincipal CustomUser user,
            @RequestBody StressResultReqDto req) {

        // 토큰이 없거나 만료된 경우. 프론트 응답 인터셉터가 401을 받아
        // 로그아웃 처리하고 로그인 화면으로 보낸다.
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 본문으로 넘어온 회원번호는 신뢰하지 않고 토큰 값으로 덮어쓴다
        req.setMemberNo(user.getMember().getMemberNo());

        return ResponseEntity.ok(stressService.calculate(req));
    }
}