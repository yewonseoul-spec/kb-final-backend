package org.scoula.mypage.service;

import org.scoula.mypage.dto.AppliedBenefitDTO;
import org.scoula.mypage.dto.FavoriteBenefitDTO;
import org.scoula.mypage.dto.GoalDTO;
import org.scoula.mypage.dto.ProfileDTO;

import java.util.List;

public interface MypageService {
    void createProfile(int memberNo, ProfileDTO dto);
    ProfileDTO getProfile(int memberNo);
    void updateProfile(int memberNo, ProfileDTO dto);
    void withdraw(int memberNo);

    void createGoal(int memberNo, GoalDTO dto);
    GoalDTO getGoal(int memberNo);
    void updateGoal(int memberNo, GoalDTO dto);
    void deleteGoal(int memberNo);

    List<AppliedBenefitDTO> getAppliedBenefits(int memberNo);
    void deleteAppliedBenefit(int memberNo, int benefitNo);
    List<FavoriteBenefitDTO> getFavoriteBenefits(int memberNo);
    void deleteFavoriteBenefit(int memberNo, int benefitNo);
}
