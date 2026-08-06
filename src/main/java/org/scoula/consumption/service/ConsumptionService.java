package org.scoula.consumption.service;

import org.scoula.consumption.dto.ConsumptionCalendarDTO;
import org.scoula.consumption.dto.ExpectedReqDTO;


public interface ConsumptionService {

    ConsumptionCalendarDTO getCal(Integer memberNo, String yearMonth);

    void addExpected(Integer memberNo, ExpectedReqDTO request);

    void updateExpected(Long expectedNo, ExpectedReqDTO request);

    void deleteExpected(Long expectedNo);

    void detectRecurringSpending(Integer memberNo);
}
