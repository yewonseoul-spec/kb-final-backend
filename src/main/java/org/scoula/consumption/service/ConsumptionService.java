package org.scoula.consumption.service;

import org.scoula.consumption.dto.ConsumptionCalendarDTO;


public interface ConsumptionService {

    ConsumptionCalendarDTO getCal(Long memberNo, String yearMonth);
}
