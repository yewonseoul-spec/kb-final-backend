package org.scoula.security.account.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberVO {
    private int memberNo;
    private String loginId;
    private String password;
    private String email;
    private String role;
    private String realName;
    private String status;
    private Date createdAt;
    private Date updatedAt;

}
