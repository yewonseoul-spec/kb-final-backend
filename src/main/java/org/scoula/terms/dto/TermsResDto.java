package org.scoula.terms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.scoula.terms.domain.TermsVO;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TermsResDto {
    private int termsNo;
    private String title;
    private String content;
    private boolean required;
    private String version;

    public static TermsResDto of(TermsVO vo) {
        return TermsResDto.builder()
                .termsNo(vo.getTermsNo())
                .title(vo.getTitle())
                .content(vo.getContent())
                .required("Y".equals(vo.getIsRequired()))
                .version(vo.getVersion())
                .build();
    }
}
