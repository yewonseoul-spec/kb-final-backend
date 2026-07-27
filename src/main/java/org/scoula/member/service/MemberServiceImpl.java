package org.scoula.member.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.scoula.member.dto.ChangePasswordDTO;
import org.scoula.member.dto.MemberDTO;
import org.scoula.member.dto.MemberJoinDTO;
import org.scoula.member.dto.MemberUpdateDTO;
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

    @Override
    public boolean checkDuplicate(String loginId) {
        MemberVO member = mapper.findByLoginId(loginId);
        return member != null;
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

}
