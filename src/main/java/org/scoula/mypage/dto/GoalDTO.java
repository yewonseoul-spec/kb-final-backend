package org.scoula.mypage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.scoula.mypage.domain.GoalType;
import org.scoula.mypage.domain.GoalVO;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalDTO {
    private GoalType goalType;

    // memberNo 는 토큰에서만 받는다 (요청 본문에 두지 않음)
    public GoalVO toVo(int memberNo) {
        return GoalVO.builder()
                .memberNo(memberNo)
                .goalType(goalType)
                .build();
    }

    public static GoalDTO of(GoalVO vo) {
        return GoalDTO.builder()
                .goalType(vo.getGoalType())
                .build();
    }
}
