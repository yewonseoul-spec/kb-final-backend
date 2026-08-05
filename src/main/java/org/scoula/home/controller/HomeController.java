package org.scoula.home.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.home.dto.HomeSummaryDTO;
import org.scoula.home.service.HomeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    public ResponseEntity<HomeSummaryDTO> getSummary() {
        return ResponseEntity.ok(homeService.getSummary());
    }
}