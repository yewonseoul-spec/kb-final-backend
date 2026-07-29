package org.scoula.mypage.service;

import org.scoula.mypage.dto.ProfileDTO;

public interface MypageService {
    void createProfile(int memberNo, ProfileDTO dto);
    ProfileDTO getProfile(int memberNo);
    void updateProfile(int memberNo, ProfileDTO dto);
}
