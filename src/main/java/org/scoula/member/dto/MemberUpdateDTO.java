package org.scoula.member.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.scoula.security.account.domain.MemberVO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberUpdateDTO {
    private String loginId;
    private String password;
    private String email;

    public MemberVO toVO() {
        return MemberVO.builder()
                .loginId(loginId)
                .email(email)
                .build();
    }
}
