package org.scoula.benefit.service;

import org.scoula.benefit.dto.YouthPolicyRequestDTO;

public interface BenefitService {
    String getYouthPolicyRaw(YouthPolicyRequestDTO requestDTO);
    int syncYouthPolicies(YouthPolicyRequestDTO requestDTO);
}