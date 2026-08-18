package org.scoula.consumption.service;

import org.scoula.consumption.dto.AiVerdictDTO;

public interface AiVerificationService {
    AiVerdictDTO verify(String requestJson, String responseJson);
}
