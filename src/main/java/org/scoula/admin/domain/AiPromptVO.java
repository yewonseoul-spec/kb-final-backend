package org.scoula.admin.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiPromptVO {
    private Integer promptNo;
    private String  promptKey;
    private Integer version;
    private String  content;
    private String  memo;
    private String  isActive;
    private Integer memberNo;
    private Date    createdAt;
}