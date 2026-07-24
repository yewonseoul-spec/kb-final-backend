package org.scoula.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.scoula.security.account.domain.MemberVO;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberJoinDTO {
    private String loginId;
    private String password;
    private String email;
    private String realName;

    public MemberVO toVO() {
        return MemberVO.builder()
                .loginId(loginId)
                .password(password)
                .email(email)
                .realName(realName)
                .build();
    }

}
