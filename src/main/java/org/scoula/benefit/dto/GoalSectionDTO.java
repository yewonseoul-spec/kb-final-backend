package org.scoula.benefit.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
public class GoalSectionDTO {

    // 이 섹션이 좁힌 중분류 이름. 화면 설명 문구와 칩에 쓴다
    private List<String> categories = Collections.emptyList();

    private List<BenefitListResDTO> benefits = Collections.emptyList();
}