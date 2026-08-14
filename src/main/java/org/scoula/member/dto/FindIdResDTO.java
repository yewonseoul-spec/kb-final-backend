package org.scoula.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.scoula.security.account.domain.MemberVO;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FindIdResDTO {
    private String loginId;
    private Date createdAt;   // 언제 만든 계정인지 확인할 단서로 화면에 같이 띄운다

    // MemberDTO 를 그대로 쓰면 비로그인 응답에 email·role·status 까지 나간다
    public static FindIdResDTO of(MemberVO m) {
        return FindIdResDTO.builder()
                .loginId(m.getLoginId())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
