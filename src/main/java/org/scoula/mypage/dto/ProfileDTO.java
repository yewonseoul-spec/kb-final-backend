package org.scoula.mypage.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.scoula.mypage.domain.MemberProfileVO;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileDTO {
    // memberNo는 타인 프로필 접근 차단을 위해 필드에서 없애고 토큰에서만 얻을 수 있도록 구현

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul") // 날짜 표현 형태 고정, 시간대 고정
    private Date birthDate;

    private String regionCode;
    private Integer income;
    private String employStatus;
    private String major;
    private Integer householdSize;
    private String education;
    private String mrgSttsCd;

    private String profileImgPath; // 응답 전용 — toVo()에 포함 x

    public MemberProfileVO toVo(int memberNo) {
        return MemberProfileVO.builder()
                .memberNo(memberNo)
                .birthDate(birthDate)
                .regionCode(regionCode)
                .income(income)
                .employStatus(employStatus)
                .major(major)
                .householdSize(householdSize)
                .education(education)
                .mrgSttsCd(mrgSttsCd)
                .build();
    }

    public static ProfileDTO of(MemberProfileVO vo) {
        return ProfileDTO.builder()
                .birthDate(vo.getBirthDate())
                .regionCode(vo.getRegionCode())
                .income(vo.getIncome())
                .employStatus(vo.getEmployStatus())
                .major(vo.getMajor())
                .householdSize(vo.getHouseholdSize())
                .education(vo.getEducation())
                .mrgSttsCd(vo.getMrgSttsCd())
                .profileImgPath(vo.getProfileImgPath())
                .build();
    }
}
