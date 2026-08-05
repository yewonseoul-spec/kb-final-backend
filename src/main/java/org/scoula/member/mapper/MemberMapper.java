package org.scoula.member.mapper;

import org.scoula.member.dto.ChangePasswordDTO;
import org.scoula.security.account.domain.MemberVO;

public interface MemberMapper {
    MemberVO get(String loginId);

    MemberVO findByLoginId(String loginId);    // id 중복 체크시 사용

    int countByEmail(String email);

    int insert(MemberVO member);  // 회원 정보 추가

    int update(MemberVO member);

    int updatePassword(ChangePasswordDTO changePasswordDTO);

    int withdraw(int memberNo);

    //추천키워드 용 멤버 조회
    Integer findMemberNoByLoginId(
            String loginId
    );

}
