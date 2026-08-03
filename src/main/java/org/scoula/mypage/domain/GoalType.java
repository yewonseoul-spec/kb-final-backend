package org.scoula.mypage.domain;

// DB 의 goal.goal_type ENUM 과 이름이 1:1 로 대응한다.
// 자바 enum 으로 받으면 잘못된 값을 Jackson 이 400 으로 걸러 준다.
public enum GoalType {
    INDEPENDENCE,
    EMPLOYMENT,
    STARTUP,
    MARRIAGE,
    STUDY_ABROAD
}
