package org.scoula.member.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.scoula.member.dto.ChangePasswordDTO;
import org.scoula.member.dto.MemberDTO;
import org.scoula.member.dto.MemberJoinDTO;
import org.scoula.member.dto.MemberUpdateDTO;
import org.scoula.member.exception.PasswordMissmatchException;
import org.scoula.member.mapper.MemberMapper;
import org.scoula.security.account.domain.MemberVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

@Log4j2
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {
    final PasswordEncoder passwordEncoder;
    final MemberMapper mapper;

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
        MemberVO member = dto.toVO();

        member.setPassword(passwordEncoder.encode(member.getPassword())); // 비밀번호 암호화
        mapper.insert(member);

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
