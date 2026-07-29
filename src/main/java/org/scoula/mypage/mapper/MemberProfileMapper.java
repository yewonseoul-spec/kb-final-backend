package org.scoula.mypage.mapper;

import org.scoula.mypage.domain.MemberProfileVO;

public interface MemberProfileMapper {
    int insert(MemberProfileVO profile);

    MemberProfileVO get(int memberNo);
}
