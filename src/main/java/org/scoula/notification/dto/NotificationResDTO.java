package org.scoula.notification.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResDTO {
    private int notiNo;
    private String notiType;
    private Integer refNo;   // 마감 알림이면 benefit_no, 아니면 null
    private String content;
    private String isRead;   // 'Y' / 'N'
    @JsonProperty("dDay")
    private Integer dDay;    // 마감 알림만 값이 있다. 0 이하면 오늘 마감

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private Date createdAt;
}