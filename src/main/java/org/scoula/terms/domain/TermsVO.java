package org.scoula.terms.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TermsVO {
    private int termsNo;
    private String title;
    private String content;
    private String isRequired; // 'Y' / 'N'
    private String termsType; // 'SIGNUP' / 'AI'
    private String version;
}
