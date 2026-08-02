package org.scoula.mypage.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.mypage.dto.GoalDTO;
import org.scoula.mypage.dto.ProfileDTO;
import org.scoula.mypage.service.MypageService;
import org.scoula.security.account.domain.CustomUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
public class MypageController {

    private final MypageService service;

    // 개인정보 최초 저장
    @PostMapping("/info")
    public ResponseEntity<String> createProfile(
            @AuthenticationPrincipal CustomUser user,
            @RequestBody ProfileDTO dto) {

        service.createProfile(user.getMember().getMemberNo(), dto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("프로필이 저장되었습니다.");
    }

    // 개인정보 조회
    @GetMapping("/info")
    public ResponseEntity<ProfileDTO> getProfile(@AuthenticationPrincipal CustomUser user) {
        return ResponseEntity.ok(service.getProfile(user.getMember().getMemberNo()));
    }

    // 개인정보 수정
    @PutMapping("/info")
    public ResponseEntity<String> updateProfile(
            @AuthenticationPrincipal CustomUser user,
            @RequestBody ProfileDTO dto) {

        service.updateProfile(user.getMember().getMemberNo(), dto);

        return ResponseEntity.ok()
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("프로필이 수정되었습니다.");
    }

    // 회원 탈퇴
    @DeleteMapping
    public ResponseEntity<String> withdraw(@AuthenticationPrincipal CustomUser user) {
        service.withdraw(user.getMember().getMemberNo());

        return ResponseEntity.ok()
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("탈퇴가 완료되었습니다.");
    }

    // 목표 최초 저장
    @PostMapping("/goal")
    public ResponseEntity<String> createGoal(
            @AuthenticationPrincipal CustomUser user,
            @RequestBody GoalDTO dto) {

        service.createGoal(user.getMember().getMemberNo(), dto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("목표가 저장되었습니다.");
    }
}
