package org.scoula.mypage.controller;

import lombok.RequiredArgsConstructor;
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
}
