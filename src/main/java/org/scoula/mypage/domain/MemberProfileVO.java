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
public class MemberProfileVO {
    private int memberNo;
    private Date birthDate;
    private String regionCode;
    private Integer income;         // 미입력 허용 → int 아닌 Integer
    private String employStatus;
    private String major;
    private Integer householdSize;  // 미입력 허용 → Integer
    private String education;
    private String mrgSttsCd;
    private String profileImgPath;  // 이미지 업로드는 별도 작업, 지금은 항상 null
    private Date updatedAt;
}
