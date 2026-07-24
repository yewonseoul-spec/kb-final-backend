package org.scoula.engine.dto;

import lombok.Data;
import java.util.Date;

//engine-01
@Data
public class UserProfileResDto {
    private int memberNo;
    private Date birthDate;
    private String regionCode;
    private Integer income;
    private String employStatus;
    private String major;
    private String education;
    private String mrgSttsCd;
}
