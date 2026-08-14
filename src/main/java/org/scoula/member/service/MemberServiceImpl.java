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

    // 닉네임: 한글·영문·숫자 2~10자. 표시용이라 중복은 허용한다.
    private static final String NICKNAME_RULE = "^[가-힣a-zA-Z0-9]{2,10}$";

    // 아이디: 영문 소문자로 시작하는 영문 소문자·숫자 5~20자.
    // login_id 컬럼 collation 이 ci 라 대문자를 허용하면 저장값과 조회값이 어긋나 보인다.
    private static final String LOGIN_ID_RULE = "^[a-z][a-z0-9]{4,19}$";

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

        changePassword.setNewPassword(passwordEncoder.encode(changePassword.getNewPassword()));

        mapper.updatePassword(changePassword);
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

}
