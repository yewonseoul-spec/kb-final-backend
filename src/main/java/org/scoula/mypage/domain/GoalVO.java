package org.scoula.mypage.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalVO {
    private int goalNo;
    private int memberNo;
    private GoalType goalType;
    private Date createdAt;
}
