package org.scoula.terms.mapper;

import org.apache.ibatis.annotations.Param;
import org.scoula.terms.domain.TermsVO;
import org.scoula.terms.dto.TermsAgreeReqDto;

import java.util.List;

public interface TermsMapper {
    List<TermsVO> getSignupTerms();

    int insertAgreements(@Param("memberNo") int memberNo,
                         @Param("agreements")List<TermsAgreeReqDto> agreements);
}
