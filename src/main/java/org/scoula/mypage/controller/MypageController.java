package org.scoula.mypage.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.member.dto.ChangePasswordDTO;
import org.scoula.member.service.MemberService;
import org.scoula.mypage.dto.*;
import org.scoula.mypage.service.MypageService;
import org.scoula.security.account.domain.CustomUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
public class MypageController {

    private final MypageService service;
    private final MemberService memberService;

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

    // 비밀번호 변경
    // loginId 는 요청 본문 값을 쓰지 않고 토큰에서 덮어쓴다.
    @PatchMapping("/password")
    public ResponseEntity<String> changePassword(
            @AuthenticationPrincipal CustomUser user,
            @RequestBody ChangePasswordDTO dto) {

        dto.setLoginId(user.getMember().getLoginId());
        memberService.changePassword(dto);

        return ResponseEntity.ok()
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("비밀번호가 변경되었습니다.");
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

    // 목표 조회
    @GetMapping("/goal")
    public ResponseEntity<GoalDTO> getGoal(@AuthenticationPrincipal CustomUser user) {
        return ResponseEntity.ok(service.getGoal(user.getMember().getMemberNo()));
    }

    // 목표 수정
    @PutMapping("/goal")
    public ResponseEntity<String> updateGoal(
            @AuthenticationPrincipal CustomUser user,
            @RequestBody GoalDTO dto) {

        service.updateGoal(user.getMember().getMemberNo(), dto);

        return ResponseEntity.ok()
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("목표가 수정되었습니다.");
    }

    // 목표 해제 ('나중에 정할래요' 로 되돌리기)
    @DeleteMapping("/goal")
    public ResponseEntity<String> deleteGoal(@AuthenticationPrincipal CustomUser user) {
        service.deleteGoal(user.getMember().getMemberNo());

        return ResponseEntity.ok()
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("목표가 해제되었습니다.");
    }

    // 신청 혜택 목록
    @GetMapping("/applied")
    public ResponseEntity<List<AppliedBenefitDTO>> getAppliedBenefits(
            @AuthenticationPrincipal CustomUser user) {

        return ResponseEntity.ok(
                service.getAppliedBenefits(user.getMember().getMemberNo()));
    }

    // 신청 혜택 삭제
    @DeleteMapping("/applied/{benefitNo}")
    public ResponseEntity<String> deleteAppliedBenefit(
            @AuthenticationPrincipal CustomUser user,
            @PathVariable int benefitNo) {

        service.deleteAppliedBenefit(user.getMember().getMemberNo(), benefitNo);

        return ResponseEntity.ok()
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("신청 혜택이 삭제되었습니다.");
    }

    // 관심 혜택 목록
    @GetMapping("/favorite")
    public ResponseEntity<List<FavoriteBenefitDTO>> getFavoriteBenefits(
            @AuthenticationPrincipal CustomUser user) {

        return ResponseEntity.ok(
                service.getFavoriteBenefits(user.getMember().getMemberNo()));
    }

    // 관심 혜택 삭제
    @DeleteMapping("/favorite/{benefitNo}")
    public ResponseEntity<String> deleteFavoriteBenefit(
            @AuthenticationPrincipal CustomUser user,
            @PathVariable int benefitNo) {

        service.deleteFavoriteBenefit(user.getMember().getMemberNo(), benefitNo);

        return ResponseEntity.ok()
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("관심 혜택이 삭제되었습니다.");
    }

    // 신청 혜택 등록 (혜택 상세 화면)
    @PostMapping("/applied")
    public ResponseEntity<String> createAppliedBenefit(
            @AuthenticationPrincipal CustomUser user,
            @RequestBody BenefitRegisterDTO dto) {

        service.createAppliedBenefit(user.getMember().getMemberNo(), dto.getBenefitNo());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("신청한 혜택에 추가되었습니다.");
    }

    // 관심 혜택 등록 (혜택 검색 결과의 하트)
    // 201 이 아니라 200 이다 — 멱등이라 '새로 만들어졌는지'를 응답으로 구분하지 않는다.
    @PostMapping("/favorite")
    public ResponseEntity<String> createFavoriteBenefit(
            @AuthenticationPrincipal CustomUser user,
            @RequestBody BenefitRegisterDTO dto) {

        service.createFavoriteBenefit(user.getMember().getMemberNo(), dto.getBenefitNo());

        return ResponseEntity.ok()
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("관심 혜택에 저장되었습니다.");
    }
}
