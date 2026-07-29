package org.scoula.mypage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.scoula.mypage.dto.ProfileDTO;
import org.scoula.mypage.mapper.MemberProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class MypageServiceImpl implements MypageService {

    private final MemberProfileMapper mapper;

    @Transactional
    @Override
    public void createProfile(int memberNo, ProfileDTO dto) {
        mapper.insert(dto.toVo(memberNo));
    }
}
