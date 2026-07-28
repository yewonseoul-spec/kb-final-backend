package org.scoula.terms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.scoula.terms.dto.TermsResDto;
import org.scoula.terms.mapper.TermsMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class TermsServiceImpl implements TermsService {
    final TermsMapper mapper;

    @Override
    public List<TermsResDto> getSignupTerms() {
        return mapper.getSignupTerms().stream()
                .map(TermsResDto::of)
                .collect(Collectors.toList());
    }
}
