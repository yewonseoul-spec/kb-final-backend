package org.scoula.mypage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.scoula.member.mapper.MemberMapper;
import org.scoula.mypage.domain.GoalVO;
import org.scoula.mypage.domain.MemberProfileVO;
import org.scoula.mypage.dto.AppliedBenefitDTO;
import org.scoula.mypage.dto.FavoriteBenefitDTO;
import org.scoula.mypage.dto.GoalDTO;
import org.scoula.mypage.dto.ProfileDTO;
import org.scoula.mypage.mapper.AppliedBenefitMapper;
import org.scoula.mypage.mapper.FavoriteBenefitMapper;
import org.scoula.mypage.mapper.GoalMapper;
import org.scoula.mypage.mapper.MemberProfileMapper;
import org.scoula.notification.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Log4j2
@Service
@RequiredArgsConstructor
public class MypageServiceImpl implements MypageService {

    private final MemberProfileMapper mapper;
    private final MemberMapper memberMapper;
    private final GoalMapper goalMapper;
    private final AppliedBenefitMapper appliedBenefitMapper;
    private final FavoriteBenefitMapper favoriteBenefitMapper;
    private final NotificationService notificationService;

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
        try {
            notificationService.notifyProfileUpdated(memberNo);
        } catch (Exception e) {
            log.warn("정보 수정 알림 생성 실패 - memberNo={}",
                    memberNo, e);
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

    @Transactional(readOnly = true)
    @Override
    public GoalDTO getGoal(int memberNo) {
        GoalVO vo = Optional.ofNullable(goalMapper.get(memberNo))
                .orElseThrow(NoSuchElementException::new);
        return GoalDTO.of(vo);
    }

    @Transactional
    @Override
    public void updateGoal(int memberNo, GoalDTO dto) {
        if (goalMapper.update(dto.toVo(memberNo)) == 0) {
            throw new NoSuchElementException();
        }
    }

    // 목표 해제. 이미 없어도 성공으로 본다.
    @Transactional
    @Override
    public void deleteGoal(int memberNo) {
        goalMapper.delete(memberNo);
    }

    // 이미 담긴 혜택이면 DuplicateKeyException → 409. 매퍼 주석 참고.
    @Transactional
    @Override
    public void createAppliedBenefit(int memberNo, int benefitNo) {
        appliedBenefitMapper.insert(memberNo, benefitNo);
    }

    // 빈 목록은 오류가 아니다. 안내 문구는 화면이 처리한다.
    @Transactional(readOnly = true)
    @Override
    public List<AppliedBenefitDTO> getAppliedBenefits(int memberNo) {
        return appliedBenefitMapper.findByMemberNo(memberNo);
    }

    // 대상이 없으면 404. 목록이 낡았다는 뜻이므로 조용히 성공시키지 않는다.
    @Transactional
    @Override
    public void deleteAppliedBenefit(int memberNo, int benefitNo) {
        if (appliedBenefitMapper.delete(memberNo, benefitNo) == 0) {
            throw new NoSuchElementException();
        }
    }

    // 멱등이다. 이미 담겨 있어도 성공으로 본다.
    @Transactional
    @Override
    public void createFavoriteBenefit(int memberNo, int benefitNo) {
        favoriteBenefitMapper.insert(memberNo, benefitNo);
    }

    // 빈 목록은 오류가 아니다. 안내 문구는 화면이 처리한다.
    @Transactional(readOnly = true)
    @Override
    public List<FavoriteBenefitDTO> getFavoriteBenefits(int memberNo) {
        return favoriteBenefitMapper.findByMemberNo(memberNo);
    }

    // 대상이 없으면 404. 목록이 낡았다는 뜻이므로 조용히 성공시키지 않는다.
    @Transactional
    @Override
    public void deleteFavoriteBenefit(int memberNo, int benefitNo) {
        if (favoriteBenefitMapper.delete(memberNo, benefitNo) == 0) {
            throw new NoSuchElementException();
        }
    }
}
