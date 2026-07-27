package org.scoula.terms.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.scoula.terms.dto.TermsResDto;
import org.scoula.terms.service.TermsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/terms")
public class TermsController {
    final TermsService service;

    @GetMapping
    public ResponseEntity<List<TermsResDto>> getTerms() {
        return ResponseEntity.ok(service.getSignupTerms());
    }

}
