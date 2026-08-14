package org.scoula.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResetPasswordReqDTO {
    private String loginId;
    private String email;
    private String newPassword;   // 본인확인 요청에서는 비어 있다
}
