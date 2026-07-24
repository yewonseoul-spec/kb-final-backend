package org.scoula.benefit.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.benefit.dto.YouthPolicyRequestDTO;
import org.scoula.benefit.service.BenefitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import static io.jsonwebtoken.Jwts.header;

@RestController
@RequestMapping("/api/benefits")
@RequiredArgsConstructor
public class BenefitController {

    private final BenefitService benefitService;

    @GetMapping(
            value = "/external/youth-center/raw",
            produces = "application/json; charset=UTF-8"
    )
    public ResponseEntity<String> getYouthCenterRaw(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(defaultValue = "json") String rtnType,
            @RequestParam(required = false) String plcyNm,
            @RequestParam(required = false) String plcyKywdNm,
            @RequestParam(required = false) String lclsfNm,
            @RequestParam(required = false) String mclsfNm,
            @RequestParam(required = false) String zipCd
    ) {
        YouthPolicyRequestDTO requestDTO = new YouthPolicyRequestDTO();
        requestDTO.setPageNum(pageNum);
        requestDTO.setPageSize(pageSize);
        requestDTO.setRtnType(rtnType);
        requestDTO.setPlcyNm(plcyNm);
        requestDTO.setPlcyKywdNm(plcyKywdNm);
        requestDTO.setLclsfNm(lclsfNm);
        requestDTO.setMclsfNm(mclsfNm);
        requestDTO.setZipCd(zipCd);

        String response = benefitService.getYouthPolicyRaw(requestDTO);

        return ResponseEntity.ok()
                .header("Content-Type", "application/json; charset=UTF-8")
                .body(response);
    }

    @PostMapping("/sync/youth-center")
    public ResponseEntity<Map<String, Object>> syncYouthCenterPolicies(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(defaultValue = "json") String rtnType,
            @RequestParam(required = false) String plcyNm,
            @RequestParam(required = false) String lclsfNm,
            @RequestParam(required = false) String mclsfNm,
            @RequestParam(required = false) String zipCd
    ) {
        YouthPolicyRequestDTO requestDTO = new YouthPolicyRequestDTO();
        requestDTO.setPageNum(pageNum);
        requestDTO.setPageSize(pageSize);
        requestDTO.setRtnType(rtnType);
        requestDTO.setPlcyNm(plcyNm);
        requestDTO.setLclsfNm(lclsfNm);
        requestDTO.setMclsfNm(mclsfNm);
        requestDTO.setZipCd(zipCd);

        int savedCount = benefitService.syncYouthPolicies(requestDTO);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "온통청년 정책 데이터 동기화 완료");
        result.put("savedCount", savedCount);

        return ResponseEntity.ok(result);

    }
}