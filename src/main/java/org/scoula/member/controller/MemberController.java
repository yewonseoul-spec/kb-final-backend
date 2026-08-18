package org.scoula.member.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.scoula.member.dto.*;
import org.scoula.member.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.scoula.member.exception.InvalidRefreshTokenException;
import org.scoula.security.account.domain.CustomUser;
import org.scoula.security.account.dto.AuthResultDTO;
import org.scoula.security.account.dto.UserInfoDTO;
import org.scoula.security.service.RefreshTokenService;
import org.scoula.security.util.JwtProcessor;
import org.springframework.security.core.userdetails.UserDetailsService;

@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class MemberController {
    final MemberService service;
    final JwtProcessor jwtProcessor;
    final RefreshTokenService refreshTokenService;
    final UserDetailsService userDetailsService;

    @PostMapping("/signup")
    public ResponseEntity<MemberDTO> signup(@RequestBody MemberJoinDTO member) {
        return ResponseEntity.ok(service.join(member));
    }

    @GetMapping("/check-id")
    public ResponseEntity<Boolean> checkId(@RequestParam("loginId") String loginId) {
        return ResponseEntity.ok().body(service.checkDuplicate(loginId));
    }

    @GetMapping("/check-email")
    public ResponseEntity<Boolean> checkEmail(@RequestParam("email") String email) {
        return ResponseEntity.ok().body(service.checkDuplicateEmail(email));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout() {
        return ResponseEntity.ok()
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("로그아웃 되었습니다.");
    }

    @PostMapping("/find-id")
    public ResponseEntity<FindIdResDTO> findId(@RequestBody FindIdReqDTO request) {
        return ResponseEntity.ok(service.findId(request));
    }

    @PostMapping("/reset-password-verify")
    public ResponseEntity<Void> verifyResetPassword(@RequestBody ResetPasswordReqDTO request) {
        service.verifyResetPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordReqDTO request) {
        service.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResultDTO> refresh(@RequestBody
                                                 RefreshReqDTO request) {
        String refreshToken = request.getRefreshToken();

        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new InvalidRefreshTokenException("다시 로그인해 주세요.");
        }

        int memberNo;
        String username;
        try {
            // 서명·만료 검증. 깨졌거나 만료면 예외가 난다
            jwtProcessor.validateToken(refreshToken);

            // 액세스 토큰을 여기로 보내는 것을 막는다
            if (!jwtProcessor.isRefreshToken(refreshToken)) {
                throw new InvalidRefreshTokenException("다시 로그인해 주세요.");
            }

            memberNo = jwtProcessor.getMemberNo(refreshToken);
            username = jwtProcessor.getUsername(refreshToken);
        } catch (InvalidRefreshTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidRefreshTokenException("다시 로그인해 주세요.");
        }

        // 로그아웃했거나 다른 기기에서 다시 로그인해 덮인 토큰이면 여기서 걸린다
        if (!refreshTokenService.matches(memberNo, refreshToken)) {
            throw new InvalidRefreshTokenException("다시 로그인해 주세요.");
        }

        CustomUser user = (CustomUser)
                userDetailsService.loadUserByUsername(username);
        String newToken = jwtProcessor.generateToken(username);

        // 리프레시 토큰은 회전하지 않으므로 받은 값을 그대로 돌려준다
        return ResponseEntity.ok(
                new AuthResultDTO(newToken, refreshToken,
                        UserInfoDTO.of(user.getMember())));
    }

}
