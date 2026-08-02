package org.scoula.mypage.mapper;

import org.scoula.mypage.domain.GoalVO;

public interface GoalMapper {
    int insert(GoalVO vo);

    GoalVO get(int memberNo);

    int update(GoalVO vo);

    int delete(int memberNo);
}
