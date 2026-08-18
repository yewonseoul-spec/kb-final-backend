package org.scoula.member.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.scoula.member.dto.*;
import org.scoula.member.exception.AccountNotFoundException;
import org.scoula.member.exception.InvalidMemberFormatException;
import org.scoula.member.exception.PasswordMissmatchException;
import org.scoula.member.exception.RequiredTermsNotAgreedException;
import org.scoula.member.mapper.MemberMapper;
import org.scoula.security.account.domain.MemberVO;
import org.scoula.terms.domain.TermsVO;
import org.scoula.terms.dto.TermsAgreeReqDto;
import org.scoula.terms.mapper.TermsMapper;
import org.scoula.notification.service.NotificationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Log4j2
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {
    final PasswordEncoder passwordEncoder;
    final MemberMapper mapper;
    final TermsMapper termsMapper;
    final NotificationService notificationService;

    // 닉네임: 한글·영문·숫자 2~10자. 표시용이라 중복은 허용한다.
    private static final String NICKNAME_RULE = "^[가-힣a-zA-Z0-9]{2,10}$";

    // 아이디: 영문 소문자로 시작하는 영문 소문자·숫자 5~20자.
    // login_id 컬럼 collation 이 ci 라 대문자를 허용하면 저장값과 조회값이 어긋나 보인다.
    private static final String LOGIN_ID_RULE = "^[a-z][a-z0-9]{4,19}$";

    // 비밀번호: 영문·숫자·특수문자를 포함한 8자 이상. SignUp.vue·ChangePassword.vue 와 같은 규칙.
    private static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$";

    // 아이디가 틀렸는지 이메일이 틀렸는지 구분해 알려주면 계정 열거에 쓰인다. 문구를 하나로 고정한다.
    private static final String NO_ACCOUNT_MESSAGE = "아이디와 이메일이 일치하는 계정이 없어요.";

    @Override
    public boolean checkDuplicate(String loginId) {
        MemberVO member = mapper.findByLoginId(loginId);
        return member != null;
    }

    @Override
    public boolean checkDuplicateEmail(String email) {
        return mapper.countByEmail(email) > 0;
    }

    @Override
    public MemberDTO get(String loginId) {
        MemberVO member = Optional.ofNullable(mapper.get(loginId))
                .orElseThrow(NoSuchElementException::new);
        return MemberDTO.of(member);
    }

    @Transactional
    @Override
    public MemberDTO join(MemberJoinDTO dto) {
        validateFormat(dto);

        // SignUp 약관 목록
        List<TermsVO> signupTerms = termsMapper.getSignupTerms();

        // 사용자가 동의한 약관 번호 모으기
        Set<Integer> agreedNos = new HashSet<>();
        if (dto.getTerms() != null) {
            for (TermsAgreeReqDto t : dto.getTerms()) {
                if (t.isAgreed()) {
                    agreedNos.add(t.getTermsNo());
                }
            }
        }

        // 필수 약관 검증 + 저장할 동의 목록 만들기
        List<TermsAgreeReqDto> agreements = new ArrayList<>();
        for (TermsVO terms : signupTerms) {
            boolean agreed = agreedNos.contains(terms.getTermsNo());

            if("Y".equals(terms.getIsRequired()) && !agreed) {
                throw new RequiredTermsNotAgreedException();
            }
            agreements.add(new TermsAgreeReqDto(terms.getTermsNo(), agreed));
        }

        // 회원 저장
        MemberVO member = dto.toVO();

        member.setPassword(passwordEncoder.encode(member.getPassword())); // 비밀번호 암호화
        mapper.insert(member);

        if (!agreements.isEmpty()) {
            termsMapper.insertAgreements(member.getMemberNo(), agreements);
        }

        return get(member.getLoginId());
    }

    // 프론트(SignUp.vue)와 같은 규칙을 쓴다. 한쪽만 바꾸면 통과한 값이 400 을 받는 구간이 생기므로 함께 고칠 것.
    private void validateFormat(MemberJoinDTO dto) {
        String loginId = dto.getLoginId();
        if (loginId == null || !loginId.matches(LOGIN_ID_RULE)) {
            throw new InvalidMemberFormatException("아이디 형식이 올바르지 않습니다.");
        }

        // 프론트는 이미 trim 해서 보내지만, API 를 직접 호출하는 경우까지 막는다
        String realName = dto.getRealName() == null ? "" : dto.getRealName().trim();
        if (!realName.matches(NICKNAME_RULE)) {
            throw new InvalidMemberFormatException("닉네임 형식이 올바르지 않습니다.");
        }
        dto.setRealName(realName);

        validatePassword(dto.getPassword());   // 암호화 전이라 원문이 들어 있다
    }

    // 회원가입·비밀번호 변경·비밀번호 재설정 세 곳이 같은 규칙을 쓴다
    private void validatePassword(String password) {
        if (password == null || !password.matches(PASSWORD_RULE)) {
            throw new InvalidMemberFormatException("비밀번호 형식이 올바르지 않습니다.");
        }
    }

    @Override
    public MemberDTO update(MemberUpdateDTO member) {
        MemberVO vo = mapper.get(member.getLoginId());
        if (!passwordEncoder.matches(member.getPassword(), vo.getPassword())) {  // 비밀번호 일치 확인
            throw new PasswordMissmatchException();
        }

        mapper.update(member.toVO());
        return get(member.getLoginId());

    }

    @Override
    public void changePassword(ChangePasswordDTO changePassword) {
        MemberVO member = mapper.get(changePassword.getLoginId());

        if (!passwordEncoder.matches(changePassword.getOldPassword(), member.getPassword())) {
            throw new PasswordMissmatchException();
        }

        // 현재 비밀번호 확인을 먼저 통과해야 형식 오류를 알려준다
        validatePassword(changePassword.getNewPassword());

        changePassword.setNewPassword(passwordEncoder.encode(changePassword.getNewPassword()));

        mapper.updatePassword(changePassword);

        // 알림 생성이 실패해도 비밀번호 변경은 성공으로 남아야 한다
        try {
            notificationService.notifyPasswordChanged(member.getMemberNo());
        } catch (Exception e) {
            log.warn("비밀번호 변경 알림 생성 실패 - memberNo={}", member.getMemberNo(), e);
        }
    }

    // 이메일은 UNIQUE 라 계정은 최대 1건이다.
    @Override
    public FindIdResDTO findId(FindIdReqDTO request) {
        MemberVO member = mapper.findByEmail(request.getEmail());
        if (member == null) {
            throw new AccountNotFoundException("입력하신 이메일로 가입된 계정이 없어요.");
        }
        return FindIdResDTO.of(member);
    }

    // AUTH-07. 비밀번호를 입력시키기 전에 걸러내는 단계라 조회만 한다.
    @Override
    public void verifyResetPassword(ResetPasswordReqDTO request) {
        findForReset(request);
    }

    @Override
    public void resetPassword(ResetPasswordReqDTO request) {
        // 프론트와 별개로 여기서 다시 조회한다.
        // 재설정 토큰을 두지 않는 대신 이 재검증이 그 자리를 맡는다.
        MemberVO member = findForReset(request);

        String newPassword = request.getNewPassword();
        validatePassword(newPassword);

        // updatePassword 의 UPDATE 문은 newPassword·loginId 만 참조하므로 oldPassword 는 비워 둔다.
        // 입력값이 아니라 조회된 loginId 를 쓴다.
        mapper.updatePassword(new ChangePasswordDTO(
                member.getLoginId(), null, passwordEncoder.encode(newPassword)));
    }

    private MemberVO findForReset(ResetPasswordReqDTO request) {
        MemberVO member = mapper.findByLoginIdAndEmail(request.getLoginId(), request.getEmail());
        if (member == null) {
            throw new AccountNotFoundException(NO_ACCOUNT_MESSAGE);
        }
        return member;
    }

}
