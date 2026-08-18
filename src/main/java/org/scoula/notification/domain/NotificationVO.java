package org.scoula.notification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationVO {
    private int notiNo;
    private int memberNo;
    private String notiType;  // DEADLINE / ACCOUNT / SECURITY
    private Integer refNo;    // 마감 알림만 값이 있다 (benefit_no)
    private String content;
    private String isRead;    // 'Y' / 'N'
    private Date createdAt;
}