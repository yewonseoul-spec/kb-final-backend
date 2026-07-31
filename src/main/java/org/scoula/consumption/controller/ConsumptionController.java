package org.scoula.consumption.controller;

import org.scoula.consumption.dto.ConsumptionCalendarDTO;
import org.scoula.consumption.service.ConsumptionService;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/consumption/calendar")
public class ConsumptionController {

    private final ConsumptionService consumptionService;

    public ConsumptionController(ConsumptionService consumptionService) {
        this.consumptionService = consumptionService;
    }

    @GetMapping("/{yearMonth}")
    public ConsumptionCalendarDTO getCal(@PathVariable("yearMonth") String yearMonth) {
        Long memberNo = 2L;

        return consumptionService.getCal(memberNo, yearMonth);
    }
}
