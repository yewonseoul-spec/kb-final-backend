package org.scoula.mypage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.scoula.member.mapper.MemberMapper;
import org.scoula.mypage.domain.MemberProfileVO;
import org.scoula.mypage.dto.GoalDTO;
import org.scoula.mypage.dto.ProfileDTO;
import org.scoula.mypage.mapper.GoalMapper;
import org.scoula.mypage.mapper.MemberProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

@Log4j2
@Service
@RequiredArgsConstructor
public class MypageServiceImpl implements MypageService {

    private final MemberProfileMapper mapper;
    private final MemberMapper memberMapper;
    private final GoalMapper goalMapper;

    @Transactional
    @Override
    public void createProfile(int memberNo, ProfileDTO dto) {
        mapper.insert(dto.toVo(memberNo));
    }

    @Transactional(readOnly = true)
    @Override
    public ProfileDTO getProfile(int memberNo) {
        MemberProfileVO vo = Optional.ofNullable(mapper.get(memberNo))
                .orElseThrow(NoSuchElementException::new);
        return ProfileDTO.of(vo);
    }

    @Transactional
    @Override
    public void updateProfile(int memberNo, ProfileDTO dto) {
        if (mapper.update(dto.toVo(memberNo)) == 0) {
            throw new NoSuchElementException();
        }
    }

    @Transactional
    @Override
    public void withdraw(int memberNo) {
        if (memberMapper.withdraw(memberNo) == 0) {
            throw new NoSuchElementException();
        }
    }

    @Transactional
    @Override
    public void createGoal(int memberNo, GoalDTO dto) {
        goalMapper.insert(dto.toVo(memberNo));
    }
}
