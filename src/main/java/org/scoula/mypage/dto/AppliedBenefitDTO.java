package org.scoula.mypage.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import org.scoula.benefit.dto.BenefitListResDTO;

import java.util.Date;

// 카드(BenefitCard)가 읽는 필드는 전부 BenefitListResDTO 에서 상속받는다.
// 여기엔 마이페이지만 아는 것 — 언제 신청했는지 — 만 둔다.
@Getter
@Setter
public class AppliedBenefitDTO extends BenefitListResDTO {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private Date appliedAt;
}