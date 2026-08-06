package org.scoula.consumption.controller;

import org.scoula.consumption.dto.ConsumptionCalendarDTO;
import org.scoula.consumption.service.ConsumptionService;
import org.scoula.security.account.domain.CustomUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/consumption/calendar")
public class ConsumptionController {

    private final ConsumptionService consumptionService;

    public ConsumptionController(ConsumptionService consumptionService) {
        this.consumptionService = consumptionService;
    }

    @GetMapping("/{yearMonth}")
    public ConsumptionCalendarDTO getCal(@PathVariable("yearMonth") String yearMonth, @AuthenticationPrincipal CustomUser user) {
        Integer memberNo = user.getMember().getMemberNo();

        return consumptionService.getCal(memberNo, yearMonth);
    }
}
