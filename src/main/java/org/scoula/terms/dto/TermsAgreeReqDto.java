package org.scoula.terms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TermsAgreeReqDto {
    private int termsNo;
    private boolean agreed;
}
