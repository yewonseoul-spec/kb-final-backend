package org.scoula.consumption.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("consumption/calender")
public class ConsumptionController {

    @GetMapping("/{yearMonth}")
    public void getCal(@PathVariable("yearMonth") int yearMonth) {
        Integer memberNo = 2;

    }
}
