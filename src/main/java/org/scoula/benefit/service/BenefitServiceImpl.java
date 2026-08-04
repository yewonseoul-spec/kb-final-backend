package org.scoula.benefit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.scoula.benefit.client.YouthPolicyApiClient;
import org.scoula.benefit.domain.BenefitVO;
import org.scoula.benefit.dto.*;
import org.scoula.benefit.mapper.BenefitMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BenefitServiceImpl implements BenefitService {

    private void saveBenefitMappings(Integer benefitNo, YouthPolicyApiItemDTO item) {


        // 기존 매핑 데이터 삭제
        benefitMapper.deleteBenefitRegions(benefitNo);
        benefitMapper.deleteBenefitMajors(benefitNo);
        benefitMapper.deleteBenefitSchools(benefitNo);
        benefitMapper.deleteBenefitJobs(benefitNo);

        // 새 매핑 데이터 저장
        saveRegionMappings(benefitNo, item.getZipCd());
        saveMajorMappings(benefitNo, item.getPlcyMajorCd());
        saveSchoolMappings(benefitNo, item.getSchoolCd());
        saveJobMappings(benefitNo, item.getJobCd());
    }

    private void saveRegionMappings(Integer benefitNo, String zipCd) {
        for (String code : splitCodes(zipCd)) {
            int inserted = benefitMapper.insertBenefitRegion(benefitNo, code);

            if (inserted == 0) {
                System.out.println("region 테이블에 없어서 저장 안 된 zipCd = " + code);
            }
        }
    }

    private void saveMajorMappings(Integer benefitNo, String plcyMajorCd) {
        for (String code : splitCodes(plcyMajorCd)) {
            benefitMapper.insertBenefitMajor(benefitNo, code);
        }
    }

    private void saveSchoolMappings(Integer benefitNo, String schoolCd) {
        for (String code : splitCodes(schoolCd)) {
            benefitMapper.insertBenefitSchool(benefitNo, code);
        }
    }

    private void saveJobMappings(Integer benefitNo, String jobCd) {
        for (String code : splitCodes(jobCd)) {
            benefitMapper.insertBenefitJob(benefitNo, code);
        }
    }

    private List<String> splitCodes(String codes) {
        if (codes == null || codes.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(codes.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    //is_active 설정
    private String mapIsActive(String aplyPrdSeCd, String applyStartDate, String applyEndDate) {
        if (aplyPrdSeCd == null || aplyPrdSeCd.trim().isEmpty()) {
            return "N";
        }

        // 0057002: 상시
        if ("0057002".equals(aplyPrdSeCd)) {
            return "Y";
        }

        // 0057003: 마감
        if ("0057003".equals(aplyPrdSeCd)) {
            return "N";
        }

        // 0057001: 특정기간
        if ("0057001".equals(aplyPrdSeCd)) {
            LocalDate today = LocalDate.now();

            LocalDate startDate = parseLocalDate(applyStartDate);
            LocalDate endDate = parseLocalDate(applyEndDate);

            if (startDate == null || endDate == null) {
                return "N";
            }

            boolean afterOrSameStart = !today.isBefore(startDate);
            boolean beforeOrSameEnd = !today.isAfter(endDate);

            return afterOrSameStart && beforeOrSameEnd ? "Y" : "N";
        }

        return "N";
    }

    //날짜 변환 메서드
    private LocalDate parseLocalDate(String dateText) {
        if (dateText == null || dateText.trim().isEmpty()) {
            return null;
        }

        String value = dateText.trim();

        try {
            //2026-07-28
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(value);
            }
            //20260728
            if (value.matches("\\d{8}")) {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
            //2026-07-28 14:51:16
            if (value.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                return LocalDate.parse(value.substring(0, 10));
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    private boolean shouldUpdateStatus(BenefitVO benefit) {
        String aplyPrdSeCd = benefit.getAplyPrdSeCd();

        // 0057002: 상시
        // 상시는 계속 조회수와 상태 갱신
        if ("0057002".equals(aplyPrdSeCd)) {
            return true;
        }

        // 0057003: 마감
        // 이미 마감 코드면 갱신하지 않음
        if ("0057003".equals(aplyPrdSeCd)) {
            return false;
        }

        // 0057001: 특정기간
        if ("0057001".equals(aplyPrdSeCd)) {
            LocalDate today = LocalDate.now();

            LocalDate endDate = parseLocalDate(benefit.getApplyEndDate());

            // 종료일을 못 읽으면 마감 여부를 확정할 수 없으므로 일단 갱신 대상
            if (endDate == null) {
                return true;
            }

            // 종료일이 오늘보다 이전이면 이미 마감된 혜택이므로 갱신 제외
            if (endDate.isBefore(today)) {
                return false;
            }

            // 시작 전이거나 진행 중이면 계속 갱신
            return true;
        }

        // 알 수 없는 코드면 안전하게 갱신 대상에 포함
        return true;
    }

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

            // 1. benefit 기본 정보 저장
            benefitMapper.upsertBenefit(benefit);

            // 2. 방금 저장/수정된 benefit_no 조회
            Integer benefitNo = benefitMapper.findBenefitNoByPlcyNo(item.getPlcyNo());

            // 3. 매핑 테이블 저장
            if (benefitNo != null) {
                saveBenefitMappings(benefitNo, item);
            }

            count++;
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

            // 마지막 페이지처럼 youthPolicyList: [] 인 경우 정상 종료 처리
            if (listNode.isEmpty()) {
                return new ArrayList<>();
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

        // 온통청년 API의 정책 목록 필드명을 직접 확인
        if (node.isObject() && node.has("youthPolicyList")) {
            JsonNode youthPolicyListNode = node.get("youthPolicyList");

            if (youthPolicyListNode != null && youthPolicyListNode.isArray()) {
                return youthPolicyListNode;
            }
        }

        // 배열 안에 정책 객체가 있는 경우
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.has("plcyNo") || item.has("plcyNm")) {
                    return node;
                }
            }
        }

        // 하위 노드 재귀 탐색
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
        vo.setApplyEndDate(parseApplyEndDate(item.getAplyYmd()));
        vo.setAplyPrdSeCd(item.getAplyPrdSeCd());
        vo.setIsActive(
                mapIsActive(
                        vo.getAplyPrdSeCd(),
                        vo.getApplyStartDate(),
                        vo.getApplyEndDate()
                )
        );

        vo.setAplyUrlAddr(item.getAplyUrlAddr());

        vo.setSprtTrgtMinAge(toInteger(item.getSprtTrgtMinAge()));
        vo.setSprtTrgtMaxAge(toInteger(item.getSprtTrgtMaxAge()));

        vo.setEarnCndSeCd(item.getEarnCndSeCd());
        vo.setEarnMinAmt(toInteger(item.getEarnMinAmt()));
        vo.setEarnMaxAmt(toInteger(item.getEarnMaxAmt()));
        vo.setEarnEtcCn(item.getEarnEtcCn());

        vo.setMrgSttsCd(item.getMrgSttsCd());

        vo.setConflictGroupCode(null);
        vo.setInqCnt(toInteger(item.getInqCnt()));

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
        if (lclsfNm == null || lclsfNm.trim().isEmpty()) {
            return "0";
        }

        if (lclsfNm.contains("일자리")) {
            return "1";
        }

        if (lclsfNm.contains("주거")) {
            return "2";
        }

        if (lclsfNm.contains("교육")) {
            return "3";
        }

        if (lclsfNm.contains("복지") || lclsfNm.contains("문화")) {
            return "4";
        }

        if (lclsfNm.contains("참여") || lclsfNm.contains("권리")) {
            return "5";
        }

        return "0";
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

    @Override
    @Transactional
    public int syncDailyYouthPolicies() {
        int pageNum = 1;
        int pageSize = 100;
        int count = 0;

        List<String> apiPlcyNoList = new ArrayList<>();

        while (true) {
            YouthPolicyRequestDTO requestDTO = new YouthPolicyRequestDTO();
            requestDTO.setPageNum(pageNum);
            requestDTO.setPageSize(pageSize);
            requestDTO.setRtnType("json");

            String json;

            try {
                json = youthPolicyApiClient.getPoliciesRaw(requestDTO);
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }

            // 스케쥴러 실패사유 기록추가
            List<YouthPolicyApiItemDTO> policyList;

            try {
                policyList = parsePolicyList(json);
            } catch (Exception e) {
                System.out.println("[온통청년 API 응답 파싱 실패] pageNum = " + pageNum);
                System.out.println("[pageSize] " + pageSize);

                if (json != null) {
                    System.out.println("[응답 앞부분]");
                    System.out.println(json.substring(0, Math.min(json.length(), 1000)));
                }

                System.out.println("[실패 사유] " + e.getMessage());
                break;
            }

            if (policyList.isEmpty()) {
                System.out.println("[온통청년 API 전체 조회 완료] pageNum = " + pageNum);
                break;
            }

            for (YouthPolicyApiItemDTO item : policyList) {
                apiPlcyNoList.add(item.getPlcyNo());

                BenefitVO benefit = convertToBenefitVO(item);


                int exists = benefitMapper.existsBenefitByPlcyNo(item.getPlcyNo());

                System.out.println("[정책 존재 여부] plcyNo = "
                        + item.getPlcyNo()
                        + ", exists = "
                        + exists);

                if (exists == 0) {
                    // 신규 혜택이면 전체 저장 + 매핑 저장
                    System.out.println("[신규 저장 분기] " + item.getPlcyNo());
                    benefitMapper.upsertBenefit(benefit);

                    Integer benefitNo = benefitMapper.findBenefitNoByPlcyNo(item.getPlcyNo());

                    if (benefitNo != null) {
                        saveBenefitMappings(benefitNo, item);
                    }
                } else {
                    if (shouldUpdateStatus(benefit)) {
                        // 기존 혜택 중 시작 전, 진행 중, 상시 혜택만 갱신
                        System.out.println("[기존 상태 갱신 분기] " + item.getPlcyNo());
                        benefitMapper.updateBenefitStatusOnly(benefit);
                    } else {
                        // 마감된 혜택은 갱신 제외
                        System.out.println("[마감 혜택 갱신 제외] " + item.getPlcyNo());
                    }
                }

                count++;
            }

            pageNum++;
//            스케쥴러 테스트로 페이지 설정 테스트 확인후 삭제필요
//            if (pageNum > 3) {
//                break;
//            }
        }
        return count;
    }

    //관리자 기간별 동기화 api
    @Override
    @Transactional
    public int syncYouthPoliciesByFrstRegDt(String startDate, String endDate) {
        int pageNum = 1;
        int pageSize = 600;
        int count = 0;

        LocalDate start = parseLocalDate(startDate);
        LocalDate end = parseLocalDate(endDate);

        if (start == null || end == null) {
            throw new IllegalArgumentException("시작일과 종료일은 yyyy-MM-dd 또는 yyyyMMdd 형식이어야 합니다.");
        }

        while (true) {
            YouthPolicyRequestDTO requestDTO = new YouthPolicyRequestDTO();
            requestDTO.setPageNum(pageNum);
            requestDTO.setPageSize(pageSize);
            requestDTO.setRtnType("json");

            String json;

            try {
                System.out.println("[관리자 기간 동기화 API 요청] pageNum = " + pageNum + ", pageSize = " + pageSize);
                json = youthPolicyApiClient.getPoliciesRaw(requestDTO);
            } catch (Exception e) {
                System.out.println("[관리자 기간 동기화 API 호출 실패] pageNum = " + pageNum);
                System.out.println("[실패 사유] " + e.getMessage());
                break;
            }

            List<YouthPolicyApiItemDTO> policyList;

            try {
                policyList = parsePolicyList(json);
            } catch (Exception e) {
                System.out.println("[관리자 기간 동기화 응답 파싱 실패] pageNum = " + pageNum);

                if (json != null) {
                    System.out.println("[응답 앞부분]");
                    System.out.println(json.substring(0, Math.min(json.length(), 1000)));
                }

                System.out.println("[실패 사유] " + e.getMessage());
                break;
            }

            if (policyList.isEmpty()) {
                System.out.println("[관리자 기간 동기화 전체 페이지 조회 완료] pageNum = " + pageNum);
                break;
            }

            for (YouthPolicyApiItemDTO item : policyList) {
                if (item.getPlcyNo() == null || item.getPlcyNo().trim().isEmpty()) {
                    continue;
                }

                LocalDate frstRegDate = parseLocalDate(item.getFrstRegDt());

                if (frstRegDate == null) {
                    continue;
                }

                boolean inPeriod = !frstRegDate.isBefore(start) && !frstRegDate.isAfter(end);

                if (!inPeriod) {
                    continue;
                }

                BenefitVO benefit = convertToBenefitVO(item);

                System.out.println("[관리자 기간 혜택 전체 갱신] plcyNo = "
                        + item.getPlcyNo()
                        + ", frstRegDt = "
                        + item.getFrstRegDt());

                benefitMapper.upsertBenefit(benefit);

                Integer benefitNo = benefitMapper.findBenefitNoByPlcyNo(item.getPlcyNo());

                if (benefitNo != null) {
                    saveBenefitMappings(benefitNo, item);
                }

                count++;
            }

            pageNum++;
        }

        return count;
    }

    //혜택 조회
    @Override
    @Transactional(readOnly = true)
    public List<BenefitListResDTO> findBenefit(
            BenefitFilterReqDTO filter
    ) {
        return benefitMapper.findBenefit(filter);
    }

    //카테고리 필터
    @Override
    public List<BenefitCategoryResDTO> findBenefitCategories() {
        return benefitMapper.findBenefitCategories();
    }

    //지역 필터
    @Override
    public List<BenefitRegionResDTO> findRegion(
            String parentRegionCode
    ) {
        return benefitMapper.findRegion(parentRegionCode);
    }
    //전공필터
    @Override
    public List<BenefitMajorResDTO> findBenefitMajors() {
        return benefitMapper.findBenefitMajors();
    }

    //학력필터
    @Override
    public List<BenefitSchoolResDTO> findBenefitSchools() {
        return benefitMapper.findBenefitSchools();
    }

    //직업필터
    @Override
    public List<BenefitJobResDTO> findBenefitJobs() {
        return benefitMapper.findBenefitJobs();
    }

    //혼인필터
    @Override
    public List<BenefitMarriageResDTO> findBenefitMarriage() {
        return benefitMapper.findBenefitMarriage();
    }

    //검색창
    private final StringRedisTemplate redisTemplate;

    private static final String RECENT_KEY_PREFIX =
            "benefit:recent-search:";

    private static final int MAX_RECENT_COUNT = 10;

    private static final Duration RECENT_TTL =
            Duration.ofDays(30);

    @Override
    public List<RecommendedKeywordResDTO>
    findRecommendedKeywords() {

        return benefitMapper.findRecommendedKeywords();
    }

    @Override
    public List<String> findRecentKeywords(
            Integer memberNo
    ) {
        if (memberNo == null) {
            return Collections.emptyList();
        }

        List<String> keywords =
                redisTemplate
                        .opsForList()
                        .range(
                                createRecentSearchKey(memberNo),
                                0,
                                MAX_RECENT_COUNT - 1
                        );

        return keywords != null
                ? keywords
                : Collections.emptyList();
    }

    @Override
    public void saveRecentKeyword(
            Integer memberNo,
            String keyword
    ) {
        if (
                memberNo == null
                        || !hasText(keyword)
        ) {
            return;
        }

        String redisKey =
                createRecentSearchKey(memberNo);

        String normalizedKeyword =
                normalizeKeyword(keyword);

        redisTemplate
                .opsForList()
                .remove(
                        redisKey,
                        0,
                        normalizedKeyword
                );

        redisTemplate
                .opsForList()
                .leftPush(
                        redisKey,
                        normalizedKeyword
                );

        redisTemplate
                .opsForList()
                .trim(
                        redisKey,
                        0,
                        MAX_RECENT_COUNT - 1
                );

        redisTemplate.expire(
                redisKey,
                RECENT_TTL
        );
    }

    @Override
    public void deleteRecentKeyword(
            Integer memberNo,
            String keyword
    ) {
        if (
                memberNo == null
                        || !hasText(keyword)
        ) {
            return;
        }

        redisTemplate
                .opsForList()
                .remove(
                        createRecentSearchKey(memberNo),
                        0,
                        normalizeKeyword(keyword)
                );
    }

    @Override
    public void deleteAllRecentKeywords(
            Integer memberNo
    ) {
        if (memberNo == null) {
            return;
        }

        redisTemplate.delete(
                createRecentSearchKey(memberNo)
        );
    }

    private String createRecentSearchKey(
            Integer memberNo
    ) {
        return RECENT_KEY_PREFIX + memberNo;
    }
    private String normalizeKeyword(
            String keyword
    ) {
        return keyword
                .trim()
                .replaceAll("\\s+", " ");
    }


}