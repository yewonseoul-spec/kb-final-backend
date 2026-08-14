package org.scoula.member.service;

import org.scoula.member.dto.*;

public interface MemberService {
    boolean checkDuplicate(String loginId);

    boolean checkDuplicateEmail(String email);

    MemberDTO get(String loginId);

    MemberDTO join(MemberJoinDTO member);

    MemberDTO update(MemberUpdateDTO member);

    void changePassword(ChangePasswordDTO changePassword);

    FindIdResDTO findId(FindIdReqDTO request);


}
