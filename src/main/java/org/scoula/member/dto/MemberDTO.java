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
public class MemberDTO {
    private Integer memberNo;
    private String loginId;
    private String email;
    private String role;
    private String realName;
    private String status;
    private Date createdAt;
    private Date updatedAt;

    public static MemberDTO of(MemberVO m) {
        return MemberDTO.builder()
                .memberNo(m.getMemberNo())
                .loginId(m.getLoginId())
                .email(m.getEmail())
                .role(m.getRole())
                .realName(m.getRealName())
                .status(m.getStatus())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }

//    public MemberVO toVO() {
//        return MemberVO.builder()
//                .username(username)
//                .email(email)
//                .regDate(regDate)
//                .updateDate(updateDate)
//                .build();
//    }
}

