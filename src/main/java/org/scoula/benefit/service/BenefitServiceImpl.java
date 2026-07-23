package org.scoula.benefit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.scoula.benefit.client.YouthPolicyApiClient;
import org.scoula.benefit.domain.BenefitVO;
import org.scoula.benefit.dto.YouthPolicyApiItemDTO;
import org.scoula.benefit.dto.YouthPolicyRequestDTO;
import org.scoula.benefit.mapper.BenefitMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BenefitServiceImpl implements BenefitService {

    private final YouthPolicyApiClient youthPolicyApiClient;
    private final BenefitMapper benefitMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getYouthPolicyRaw(YouthPolicyRequestDTO requestDTO) {
        return youthPolicyApiClient.getPoliciesRaw(requestDTO);
    }

    @Override
    @Transactional
    public int syncYouthPolicies(YouthPolicyRequestDTO requestDTO) {
        String json = youthPolicyApiClient.getPoliciesRaw(requestDTO);

        List<YouthPolicyApiItemDTO> policyList = parsePolicyList(json);

        int count = 0;

        for (YouthPolicyApiItemDTO item : policyList) {
            BenefitVO benefit = convertToBenefitVO(item);
            count += benefitMapper.upsertBenefit(benefit);
        }

        return count;
    }

    private List<YouthPolicyApiItemDTO> parsePolicyList(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode listNode = findPolicyArray(root);

            if (listNode == null || !listNode.isArray()) {
                throw new IllegalStateException("온통청년 API 응답에서 정책 목록 배열을 찾지 못했습니다.");
            }

            List<YouthPolicyApiItemDTO> result = new ArrayList<>();

            for (JsonNode node : listNode) {
                YouthPolicyApiItemDTO item = objectMapper.treeToValue(node, YouthPolicyApiItemDTO.class);
                result.add(item);
            }

            return result;
        } catch (Exception e) {
            throw new RuntimeException("온통청년 API 응답 파싱 실패", e);
        }
    }

    private JsonNode findPolicyArray(JsonNode node) {
        if (node == null) {
            return null;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.has("plcyNo") || item.has("plcyNm")) {
                    return node;
                }
            }
        }

        if (node.isObject()) {
            for (JsonNode child : node) {
                JsonNode result = findPolicyArray(child);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    private BenefitVO convertToBenefitVO(YouthPolicyApiItemDTO item) {
        BenefitVO vo = new BenefitVO();

        vo.setPlcyNo(item.getPlcyNo());
        vo.setPlcyNm(item.getPlcyNm());
        vo.setCategoryCode(mapCategoryCode(item.getLclsfNm()));
        vo.setSprvsnInstCdNm(item.getSprvsnInstCdNm());

        vo.setTargetDesc(makeTargetDesc(item));
        vo.setPlcySprtCn(item.getPlcySprtCn());
        vo.setSupportAmount(null);

        vo.setPlcyAplyMthdCn(item.getPlcyAplyMthdCn());
        vo.setSbmsnDcmntCn(item.getSbmsnDcmntCn());

        vo.setAplyYmd(item.getAplyYmd());
        vo.setApplyStartDate(parseApplyStartDate(item.getAplyYmd()));
        vo.setApplyEndDate(parseApplyEndDate(item.getAplyYmd()));

        vo.setAplyUrlAddr(item.getAplyUrlAddr());

        vo.setSprtTrgtMinAge(toInteger(item.getSprtTrgtMinAge()));
        vo.setSprtTrgtMaxAge(toInteger(item.getSprtTrgtMaxAge()));

        vo.setEarnCndSeCd(item.getEarnCndSeCd());
        vo.setEarnMinAmt(toInteger(item.getEarnMinAmt()));
        vo.setEarnMaxAmt(toInteger(item.getEarnMaxAmt()));
        vo.setEarnEtcCn(item.getEarnEtcCn());

        vo.setMrgSttsCd(item.getMrgSttsCd());
        vo.setPlcyMajorCd(item.getPlcyMajorCd());
        vo.setSchoolCd(item.getSchoolCd());
        vo.setJobCd(item.getJobCd());

        vo.setConflictGroupCode(null);
        vo.setInqCnt(toInteger(item.getInqCnt()));

        vo.setIsActive("Y");

        vo.setFrstRegDt(item.getFrstRegDt());
        vo.setLastMdfcnDt(item.getLastMdfcnDt());

        vo.setPlcyExplnCn(item.getPlcyExplnCn());

        return vo;
    }

    private String makeTargetDesc(YouthPolicyApiItemDTO item) {
        StringBuilder sb = new StringBuilder();

        if (hasText(item.getAddAplyQlfcCndCn())) {
            sb.append(item.getAddAplyQlfcCndCn());
        }

        if (hasText(item.getPtcpPrpTrgtCn())) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("[참여 제한]\n").append(item.getPtcpPrpTrgtCn());
        }

        return sb.length() == 0 ? null : sb.toString();
    }

    private String mapCategoryCode(String lclsfNm) {
        if (lclsfNm == null) {
            return null;
        }
        if (lclsfNm.contains("일자리")) {return "1";}
        if (lclsfNm.contains("주거")) {return "2";}
        if (lclsfNm.contains("교육")) {return "3";}
        if (lclsfNm.contains("복지") || lclsfNm.contains("문화")) {return "4";}
        if (lclsfNm.contains("참여") || lclsfNm.contains("권리")) {return "5";}
        return null;
    }

    private Integer toInteger(String value) {
        if (!hasText(value)) {
            return null;
        }

        try {
            String numberOnly = value.replaceAll("[^0-9]", "");

            if (numberOnly.isEmpty()) {
                return null;
            }

            return Integer.parseInt(numberOnly);
        } catch (Exception e) {
            return null;
        }
    }

    private String parseApplyStartDate(String aplyYmd) {
        if (!hasText(aplyYmd)) {
            return null;
        }

        String[] dates = aplyYmd.replace(".", "-").split("~");

        if (dates.length >= 1) {
            return normalizeDate(dates[0]);
        }

        return null;
    }

    private String parseApplyEndDate(String aplyYmd) {
        if (!hasText(aplyYmd)) {
            return null;
        }

        String[] dates = aplyYmd.replace(".", "-").split("~");

        if (dates.length >= 2) {
            return normalizeDate(dates[1]);
        }

        return null;
    }

    private String normalizeDate(String value) {
        if (!hasText(value)) {
            return null;
        }

        String text = value.trim();

        if (text.contains("상시") || text.contains("미정") || text.contains("예산")) {
            return null;
        }

        String numberOnly = text.replaceAll("[^0-9]", "");

        if (numberOnly.length() == 8) {
            return numberOnly.substring(0, 4) + "-"
                    + numberOnly.substring(4, 6) + "-"
                    + numberOnly.substring(6, 8);
        }

        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}