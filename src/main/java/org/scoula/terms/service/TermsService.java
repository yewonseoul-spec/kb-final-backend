package org.scoula.terms.service;

import org.scoula.terms.dto.TermsResDto;

import java.util.List;

public interface TermsService {
    List<TermsResDto> getSignupTerms();
}
