package org.scoula.benefit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.scoula.benefit.client.YouthPolicyApiClient;
import org.scoula.benefit.domain.BenefitVO;
import org.scoula.benefit.dto.*;
import org.scoula.benefit.mapper.BenefitMapper;
import org.scoula.mypage.domain.GoalVO;
import org.scoula.mypage.mapper.GoalMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.scoula.admin.domain.SyncLogDetailVO;
import org.scoula.admin.domain.SyncLogVO;
import org.scoula.admin.mapper.AdminMapper;


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
    private final AdminMapper adminMapper;
    private final GoalMapper goalMapper;

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
        vo.setDetailCategoryCode(mapDetailCategoryCode(item.getMclsfNm()));
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
        vo.setRefUrlAddr1(item.getRefUrlAddr1());

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

    private String mapDetailCategoryCode(String mclsfNm) {
        if (mclsfNm == null || mclsfNm.trim().isEmpty()) {
            return null;
        }

        String value = mclsfNm.trim();

        if (value.contains("취업")) return "01";
        if (value.contains("재직자")) return "02";
        if (value.contains("창업")) return "03";
        if (value.contains("주택") || value.contains("거주지")) return "04";
        if (value.contains("기숙사")) return "05";
        if (value.contains("전월세") || value.contains("주거급여")) return "06";
        if (value.contains("미래역량강화")) return "07";
        if (value.contains("교육비")) return "08";
        if (value.contains("온라인교육")) return "09";
        if (value.contains("취약계층") || value.contains("금융지원")) return "10";
        if (value.contains("건강")) return "11";
        if (value.contains("예술인")) return "12";
        if (value.contains("문화활동")) return "13";
        if (value.contains("청년참여")) return "14";
        if (value.contains("정책인프라")) return "15";
        if (value.contains("청년국제교류")) return "16";
        if (value.contains("권익보호")) return "17";

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

    @Override
    @Transactional
    public int syncDailyYouthPolicies() {
        long startTime = System.currentTimeMillis();

        SyncRunResult finalResult = null;
        StringBuilder retryHistory = new StringBuilder();

        int maxWholeRetryCount = 1; // 전체 동기화 재시도 1회

        for (int attempt = 1; attempt <= maxWholeRetryCount + 1; attempt++) {
            System.out.println("[청년혜택 자동 동기화 시작] 전체 시도 = " + attempt);

            SyncRunResult result = runDailySyncOnce(attempt);

            finalResult = result;

            if (result.errorMsg == null) {

                if (attempt > 1) {
                    retryHistory
                            .append("\n[")
                            .append(attempt)
                            .append("차 동기화 성공]");
                }

                break;
            }

            retryHistory
                    .append("[")
                    .append(attempt)
                    .append("차 동기화 실패] ")
                    .append(result.errorMsg);

            if (attempt <= maxWholeRetryCount) {
                retryHistory.append("\n");
                System.out.println("[청년혜택 자동 동기화 전체 재시도 대기] 120초 후 pageNum=1부터 다시 시작");

                try {
                    Thread.sleep(120_000); // 2분 대기
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();

                    retryHistory.append("[전체 재시도 대기 중 인터럽트 발생] ")
                            .append(e.getMessage())
                            .append(" / ");

                    break;
                }

                System.out.println("[청년혜택 자동 동기화 전체 재시도 시작] pageNum=1부터 다시 시작");
            }
        }

        int durationMs = (int) (System.currentTimeMillis() - startTime);

        String resultStatus;

        if (finalResult.errorMsg == null) {
            resultStatus = "S";
        } else if (finalResult.count > 0) {
            resultStatus = "P";
        } else {
            resultStatus = "F";
        }

        String errorMsg = null;

        if (finalResult.errorMsg != null) {
            errorMsg = retryHistory.toString();
        } else if (retryHistory.length() > 0) {
            errorMsg = retryHistory.toString();
        }

        if (errorMsg != null && errorMsg.length() > 1000) {
            errorMsg = errorMsg.substring(0, 1000);
        }
        // [상호 수정] 반환값을 받는다. sync_log_detail 을 남기려면 log_no 가 필요하다
        Integer logNo = saveAutoSyncLog(
                resultStatus,
                finalResult.count,
                finalResult.insertCnt,
                finalResult.updateCnt,
                finalResult.skipCnt,
                finalResult.deleteCnt,   // [상호 추가]
                errorMsg,
                durationMs
        );

        // [상호 추가] 처리 내역을 sync_log_detail 에 남긴다.
        // 로그 기록이 실패하면 logNo 가 없으므로 상세도 건너뛴다.
        // 상세 기록 실패가 동기화 자체를 되돌리면 안 되므로 예외를 삼킨다.
        if (logNo != null && !finalResult.items.isEmpty()) {
            try {
                List<SyncLogDetailVO> details = new ArrayList<>();
                for (SyncedBenefitDTO item : finalResult.items) {
                    details.add(new SyncLogDetailVO(
                            logNo,
                            item.getBenefitNo(),
                            item.getActionType(),
                            item.getChangedSummary()));
                }
                adminMapper.insertSyncLogDetails(details);

                System.out.println("[자동 동기화 상세 기록] " + details.size() + "건");

            } catch (Exception e) {
                System.out.println("자동 동기화 상세 기록 실패: " + e.getMessage());
            }
        }

        System.out.println("[청년혜택 자동 동기화 완료] 처리 건수 = " + finalResult.count);

        return finalResult.count;
    }

    private SyncRunResult runDailySyncOnce(int attemptNo) {
        SyncRunResult result = new SyncRunResult();

        int pageNum = 1;
        int pageSize = 100;

        List<String> apiPlcyNoList = new ArrayList<>();

        while (true) {
            YouthPolicyRequestDTO requestDTO = new YouthPolicyRequestDTO();
            requestDTO.setPageNum(pageNum);
            requestDTO.setPageSize(pageSize);
            requestDTO.setRtnType("json");

            String json;

            try {
                System.out.println("[온통청년 API 호출] 전체시도="
                        + attemptNo
                        + ", pageNum="
                        + pageNum
                        + ", pageSize="
                        + pageSize);

                json = youthPolicyApiClient.getPoliciesRaw(requestDTO);

            } catch (Exception e) {
                result.errorMsg = "전체시도="
                        + attemptNo
                        + ", pageNum="
                        + pageNum
                        + ", API 호출 실패: "
                        + e.getMessage();

                System.out.println("[온통청년 API 호출 실패] " + result.errorMsg);

                break;
            }

            List<YouthPolicyApiItemDTO> policyList;

            try {
                policyList = parsePolicyList(json);
            } catch (Exception e) {
                result.errorMsg = "전체시도="
                        + attemptNo
                        + ", pageNum="
                        + pageNum
                        + ", 응답 파싱 실패: "
                        + e.getMessage();

                System.out.println("[온통청년 API 응답 파싱 실패] " + result.errorMsg);

                if (json != null) {
                    System.out.println("[응답 앞부분]");
                    System.out.println(json.substring(0, Math.min(json.length(), 1000)));
                }

                break;
            }

            if (policyList.isEmpty()) {
                System.out.println("[온통청년 API 전체 조회 완료] 전체시도="
                        + attemptNo
                        + ", pageNum="
                        + pageNum);

                result.completedSuccessfully = true;
                break;
            }

            for (YouthPolicyApiItemDTO item : policyList) {
                if (item.getPlcyNo() == null || item.getPlcyNo().trim().isEmpty()) {
                    result.skipCnt++;
                    result.count++;
                    continue;
                }

                apiPlcyNoList.add(item.getPlcyNo());

                BenefitVO benefit = convertToBenefitVO(item);

                int exists = benefitMapper.existsBenefitByPlcyNo(item.getPlcyNo());

                if (exists == 0) {
                    System.out.println("[신규 저장 분기] " + item.getPlcyNo());

                    benefitMapper.upsertBenefit(benefit);

                    Integer benefitNo = benefitMapper.findBenefitNoByPlcyNo(item.getPlcyNo());

                    if (benefitNo != null) {
                        saveBenefitMappings(benefitNo, item);

                        // [상호 추가] 신규 등록 건을 상세에 남긴다
                        result.items.add(new SyncedBenefitDTO(benefitNo, "I", null));
                    }


                    result.insertCnt++;
                } else {
                    benefitMapper.restoreBenefitFromApi(item.getPlcyNo());

                    if (shouldUpdateStatus(benefit)) {
                        System.out.println("[기존 상태 갱신 분기] " + item.getPlcyNo());

                        benefitMapper.updateBenefitStatusOnly(benefit);
                        // [상호 추가] 갱신 건을 상세에 남긴다
                        Integer benefitNo = benefitMapper.findBenefitNoByPlcyNo(item.getPlcyNo());
                        if (benefitNo != null) {
                            result.items.add(new SyncedBenefitDTO(benefitNo, "U", null));
                        }

                        result.updateCnt++;
                    } else {
                        System.out.println("[마감 혜택 갱신 제외] " + item.getPlcyNo());

                        result.skipCnt++;
                    }
                }

                result.count++;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                result.errorMsg =
                        "전체시도="
                                + attemptNo
                                + ", pageNum="
                                + pageNum
                                + ", 페이지 호출 대기 중 인터럽트 발생";

                break;
            }

            pageNum++;
        }

        if (result.completedSuccessfully && !apiPlcyNoList.isEmpty()) {
            List<String> distinctApiPlcyNoList = apiPlcyNoList.stream()
                    .filter(plcyNo -> plcyNo != null && !plcyNo.trim().isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            // [상호 추가] 숨김 처리될 정책을 미리 뽑아둔다.
            // deactivateBenefitsNotInApi 는 건수만 돌려주므로
            // 어떤 정책이 사라졌는지 남기려면 UPDATE 전에 조회해야 한다.
            List<Integer> toDelete =
                    benefitMapper.findBenefitNosNotInApi(distinctApiPlcyNoList);


            int deletedCnt = benefitMapper.deactivateBenefitsNotInApi(distinctApiPlcyNoList);

            System.out.println("[Open API 삭제 혜택 비활성화 완료] 건수 = " + deletedCnt);

            // [상호 추가] 숨김 처리 건을 상세에 남긴다
            for (Integer benefitNo : toDelete) {
                result.items.add(new SyncedBenefitDTO(
                        benefitNo, "D", "온통청년 API 응답에 없음 (데이터는 보존)"));
            }

            // [상호 수정] updateCnt 대신 deleteCnt 에 담는다
            result.deleteCnt += deletedCnt;
            result.count += deletedCnt;
        } else {
            System.out.println("[Open API 삭제 혜택 비활성화 생략] 전체 조회 실패 또는 API 목록 없음");
        }

        return result;
    }

    // OPEN API 실패 후 재호출 메서드
    private static class SyncRunResult {
        int count;
        int insertCnt;
        int updateCnt;
        int skipCnt;
        String errorMsg;
        boolean completedSuccessfully;

        // [상호 추가] 숨김 처리 건수
        // updateCnt 에 섞으면 관리자 화면에서 갱신과 구분되지 않는다
        int deleteCnt;

        // [상호 추가] 처리한 혜택 목록
        // 건수만 남기면 관리자 화면에서 무엇이 바뀌었는지 알 수 없다.
        // 기간별 동기화(syncByFrstRegDtWithDetail)와 같은 방식으로 자동 동기화에도 남긴다.
        List<SyncedBenefitDTO> items = new ArrayList<>();
    }
    /**
     * 자동 동기화 이력 기록.
     * 로그 기록이 실패해도 동기화 자체는 성공으로 두기 위해 예외를 밖으로 올리지 않는다.
     *   [상호 수정] 반환 타입을 void → Integer 로 바꿨다.
     *   상세 내역(sync_log_detail)을 남기려면 방금 만든 log_no 가 필요하다.
     */
    private Integer saveAutoSyncLog(String resultStatus, int totalCnt, int insertCnt,
                                    int updateCnt, int skipCnt, int deleteCnt,
                                    String errorMsg, int durationMs) {
        try {
            SyncLogVO log = new SyncLogVO();
            log.setExecType("A");
            log.setSyncStartDate(null);   // 전체 동기화라 기간 조건이 없다
            log.setSyncEndDate(null);
            log.setResultStatus(resultStatus);
            log.setTotalCnt(totalCnt);
            log.setInsertCnt(insertCnt);
            log.setUpdateCnt(updateCnt);
            log.setSkipCnt(skipCnt);
            log.setDeleteCnt(deleteCnt);   // [상호 추가]
            log.setErrorMsg(errorMsg == null || errorMsg.length() <= 500
                    ? errorMsg
                    : errorMsg.substring(0, 500));
            log.setDurationMs(durationMs);
            log.setMemberNo(null);        // 스케줄러 실행이라 관리자가 없다

            adminMapper.insertSyncLog(log);

            // @Options(useGeneratedKeys=true) 로 채워진 값
            return log.getLogNo();

        } catch (Exception e) {
            System.out.println("자동 동기화 이력 기록 실패: " + e.getMessage());
            return null;
        }
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

    private static final int MAX_RECENT_COUNT = 6;

    private static final Duration RECENT_TTL =
            Duration.ofHours(7);

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

        //같은 검색어가 있으면 기존 위치에서 제거
        redisTemplate
                .opsForList()
                .remove(
                        redisKey,
                        0,
                        normalizedKeyword
                );

        // 가장 최근 검색어를 맨 앞에 저장
        redisTemplate
                .opsForList()
                .leftPush(
                        redisKey,
                        normalizedKeyword
                );

        // 최근 검색어 최대 6개만 유지
        redisTemplate
                .opsForList()
                .trim(
                        redisKey,
                        0,
                        MAX_RECENT_COUNT - 1
                );

        // 마지막 검색 시점부터 7시간 후 만료
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


    // ══════════════════════════════════════════════════════════════════════
// BenefitServiceImpl.java 에 넣을 내용
// 기존 syncYouthPoliciesByFrstRegDt 는 그대로 두고 아래를 추가한다.
//
// import 추가
//   import org.scoula.benefit.dto.SyncDetailResultDTO;
// ══════════════════════════════════════════════════════════════════════


    /**
     * 관리자 기간별 동기화 (처리 내역 포함)
     *
     * syncYouthPoliciesByFrstRegDt와 동작은 같고, 처리한 혜택 목록을 함께 반환한다.
     * upsertBenefit은 INSERT와 UPDATE를 구분해주지 않고 갱신 전 값도 남기지 않으므로,
     * 호출 전에 기존 행을 읽어 신규/갱신 판별과 변경 내용 비교를 함께 처리한다.
     */
    @Override
    @Transactional
    public SyncDetailResultDTO syncByFrstRegDtWithDetail(String startDate, String endDate) {
        int pageNum = 1;
        int pageSize = 600;
        SyncDetailResultDTO result = new SyncDetailResultDTO();
        List<String> apiPlcyNoList = new ArrayList<>();
        boolean completedSuccessfully = false;

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
                System.out.println("[실패 사유] " + e.getMessage());
                break;
            }

            if (policyList.isEmpty()) {
                System.out.println("[관리자 기간 동기화 전체 페이지 조회 완료] pageNum = " + pageNum);
                completedSuccessfully = true;
                break;
            }

            for (YouthPolicyApiItemDTO item : policyList) {
                if (item.getPlcyNo() == null || item.getPlcyNo().trim().isEmpty()) {
                    continue;
                }

                // 삭제 판정은 선택 기간과 무관하게 API 전체 스냅샷을 기준으로 해야 한다.
                apiPlcyNoList.add(item.getPlcyNo());

                LocalDate frstRegDate = parseLocalDate(item.getFrstRegDt());

                if (frstRegDate == null) {
                    continue;
                }

                boolean inPeriod = !frstRegDate.isBefore(start) && !frstRegDate.isAfter(end);

                if (!inPeriod) {
                    continue;
                }

                // upsert 후에는 기존 값을 알 수 없으므로 미리 읽어 둔다
                BenefitVO before = benefitMapper.findBenefitByPlcyNo(item.getPlcyNo());
                String actionType = (before == null) ? "I" : "U";

                BenefitVO benefit = convertToBenefitVO(item);
                String changedSummary = (before == null) ? null : buildChangeSummary(before, benefit);

                benefitMapper.upsertBenefit(benefit);

                Integer benefitNo = (before != null)
                        ? before.getBenefitNo()
                        : benefitMapper.findBenefitNoByPlcyNo(item.getPlcyNo());

                if (benefitNo != null) {
                    saveBenefitMappings(benefitNo, item);
                }

                result.add(benefitNo, actionType, changedSummary);
            }

            pageNum++;
        }

        // 자동 동기화와 동일하게, 전체 페이지 조회가 정상 완료된 경우에만
        // API에서 사라진 정책을 비활성화한다. 중간 실패 시 오삭제를 막는다.
        if (completedSuccessfully && !apiPlcyNoList.isEmpty()) {
            List<String> distinctApiPlcyNoList = apiPlcyNoList.stream()
                    .filter(plcyNo -> plcyNo != null && !plcyNo.trim().isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            List<Integer> toDelete = benefitMapper.findBenefitNosNotInApi(distinctApiPlcyNoList);
            int deletedCnt = benefitMapper.deactivateBenefitsNotInApi(distinctApiPlcyNoList);

            for (Integer benefitNo : toDelete) {
                result.addDeleted(benefitNo, "온통청년 API 응답에 없음 (데이터는 보존)");
            }

            System.out.println("[관리자 기간 동기화 API 삭제 혜택 비활성화 완료] 건수 = " + deletedCnt);
        } else {
            System.out.println("[관리자 기간 동기화 API 삭제 혜택 비활성화 생략] 전체 조회 실패 또는 API 목록 없음");
        }

        return result;
    }

    /**
     * 동기화로 실제 무엇이 바뀌었는지 요약한다.
     * 조회수(inq_cnt)는 매 동기화마다 달라져 전부 '변경됨'이 되므로 비교에서 뺀다.
     * 바뀐 것이 없으면 null을 돌려주고, 화면에서는 '변경 없음'으로 표시한다.
     */
    private String buildChangeSummary(BenefitVO before, BenefitVO after) {
        StringBuilder sb = new StringBuilder();

        appendIfChanged(sb, "혜택명", before.getPlcyNm(), after.getPlcyNm());
        appendIfChanged(sb, "노출상태", before.getIsActive(), after.getIsActive());
        appendIfChanged(sb, "마감일", before.getApplyEndDate(), after.getApplyEndDate());
        appendIfChanged(sb, "신청기간구분", before.getAplyPrdSeCd(), after.getAplyPrdSeCd());
        appendIfChanged(sb, "카테고리", before.getCategoryCode(), after.getCategoryCode());
        appendIfChanged(sb, "소득조건", before.getEarnCndSeCd(), after.getEarnCndSeCd());
        appendIfChanged(sb, "최대연령",
                toText(before.getSprtTrgtMaxAge()), toText(after.getSprtTrgtMaxAge()));

        if (sb.length() == 0) {
            return null;
        }

        // changed_summary가 varchar(500)이라 길이를 보장한다
        String summary = sb.toString();
        return summary.length() > 500 ? summary.substring(0, 500) : summary;
    }

    /** 값이 달라졌을 때만 '항목 이전 → 이후' 형태로 덧붙인다 */
    private void appendIfChanged(StringBuilder sb, String label, String before, String after) {
        String b = (before == null) ? "" : before.trim();
        String a = (after == null) ? "" : after.trim();

        if (b.equals(a)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(label).append(" ")
                .append(b.isEmpty() ? "없음" : b)
                .append(" → ")
                .append(a.isEmpty() ? "없음" : a);
    }

    private String toText(Integer value) {
        return (value == null) ? "" : String.valueOf(value);
    }

    // 혜택 상세페이지
    @Override
    public BenefitDetailResDTO findBenefitDetail(
            Integer benefitNo
    ) {if (benefitNo == null) {
        throw new IllegalArgumentException(
                "혜택 번호가 필요합니다.");}

        BenefitDetailResDTO detail =
                benefitMapper.findBenefitDetail(
                        benefitNo);
        if (detail == null) {
            throw new IllegalArgumentException(
                    "존재하지 않는 혜택입니다.");}

        return detail;}

    //사용자 프로필 조건기반 혜택추천
    @Override
    public BenefitProfileFilterResDTO findBenefitProfileFilter(
            Integer memberNo
    ) {
        if (memberNo == null) {
            throw new IllegalArgumentException(
                    "회원 번호가 필요합니다."
            );
        }

        BenefitProfileFilterResDTO profileFilter =
                benefitMapper.findBenefitProfileFilter(
                        memberNo
                );

        if (profileFilter == null) {
            throw new IllegalArgumentException(
                    "회원 프로필이 존재하지 않습니다."
            );
        }

        return profileFilter;
    }

    @Override
    public GoalRecommendResDTO findGoalRecommend(
            Integer memberNo
    ) {
        if (memberNo == null) {
            throw new IllegalArgumentException(
                    "회원 번호가 필요합니다."
            );
        }

        GoalRecommendResDTO result = new GoalRecommendResDTO();

        GoalVO goal = goalMapper.get(memberNo);

        /*
         * goal 테이블 ENUM 에 '없음'이 없고 member_no 에 unique 가 걸려 있어,
         * 목표 미설정은 값이 아니라 행 자체가 없는 상태로 나타난다.
         * 이때는 기본값인 빈 섹션 두 개가 그대로 나간다.
         */
        if (goal == null || goal.getGoalType() == null) {
            return result;
        }

        String goalType = goal.getGoalType().name();

        result.setGoalType(goalType);

        /*
         * 프로필이 없으면 null 이 온다.
         * 그때는 조건 필터 없이 목표 매핑만으로 보여 준다.
         */
        BenefitProfileFilterResDTO profile =
                benefitMapper.findBenefitProfileFilter(memberNo);

        result.setPrimary(findGoalSection(goalType, 1, profile));
        result.setSecondary(findGoalSection(goalType, 2, profile));

        return result;
    }

    private GoalSectionDTO findGoalSection(
            String goalType,
            int priority,
            BenefitProfileFilterResDTO profile
    ) {
        GoalSectionDTO section = new GoalSectionDTO();

        List<String> codes =
                benefitMapper.findGoalCategoryCodes(
                        goalType, priority
                );

        /*
         * 매핑이 비면 조회하지 않고 빈 섹션을 돌려준다.
         * 그대로 findBenefit 에 넘기면 IN 조건이 통째로 빠져
         * 전체 혜택이 목표 추천으로 둔갑한다.
         */
        if (codes == null || codes.isEmpty()) {
            return section;
        }

        section.setCategories(
                benefitMapper.findGoalCategoryNames(
                        goalType, priority
                )
        );

        BenefitFilterReqDTO filter = new BenefitFilterReqDTO();

        filter.setDetailCategoryCodes(codes);

        if (profile != null) {
            filter.setAge(profile.getAge());
            filter.setZipCd(resolveZipCd(profile));
            filter.setPlcyMajorCd(profile.getPlcyMajorCd());
            filter.setSchoolCd(profile.getSchoolCd());
            filter.setJobCd(profile.getJobCd());
            filter.setMrgSttsCd(profile.getMrgSttsCd());
        }

        section.setBenefits(benefitMapper.findBenefit(filter));

        return section;
    }

    /*
     * 구군 → 시군 → 시도 순으로 가장 좁은 지역을 쓴다.
     * 조건 기반 탭이 프론트에서 하는 것과 같은 규칙이다.
     */
    private String resolveZipCd(
            BenefitProfileFilterResDTO profile
    ) {
        if (profile.getDistrictCode() != null
                && !profile.getDistrictCode().isEmpty()) {
            return profile.getDistrictCode();
        }

        if (profile.getCityCode() != null
                && !profile.getCityCode().isEmpty()) {
            return profile.getCityCode();
        }

        return profile.getProvinceCode();
    }
    //소비 기반 혜택 추천
    @Override
    public ConsumptionRecommendResDTO
    findConsumptionRecommendedBenefits(
            Integer memberNo,
            BenefitFilterReqDTO filter
    ) {

        List<ConsumptionCategoryResDTO>
                topCategories =
                benefitMapper
                        .findTopSpendingCategories(
                                memberNo
                        );

        if (topCategories == null
                || topCategories.isEmpty()) {

            return ConsumptionRecommendResDTO
                    .builder()
                    .message(
                            "아직 소비 내역이 없어 "
                                    + "소비 기반 추천을 제공하기 어려워요."
                    )
                    .spendingCategories(
                            Collections.emptyList()
                    )
                    .benefitCategories(
                            Collections.emptyList()
                    )
                    .totalCount(0)
                    .benefits(
                            Collections.emptyList()
                    )
                    .build();
        }

        List<String> spendingCategories =
                topCategories.stream()
                        .map(
                                ConsumptionCategoryResDTO
                                        ::getCategoryName
                        )
                        .collect(
                                Collectors.toList()
                        );

        List<String> benefitCategories =
                benefitMapper
                        .findConsumptionBenefitCategoryNames(
                                memberNo
                        );

        List<BenefitListResDTO> benefits =
                benefitMapper
                        .findConsumptionRecommendedBenefits(
                                memberNo,
                                filter
                        );

        String spendingText =
                String.join(
                        "·",
                        spendingCategories
                );

        String benefitText =
                String.join(
                        "·",
                        benefitCategories
                );

        String message =
                spendingText
                        + " 소비가 많은 패턴을 바탕으로 "
                        + benefitText
                        + " 혜택을 추천했어요.";

        return ConsumptionRecommendResDTO
                .builder()
                .message(message)
                .spendingCategories(
                        spendingCategories
                )
                .benefitCategories(
                        benefitCategories
                )
                .totalCount(
                        benefits.size()
                )
                .benefits(
                        benefits
                )
                .build();
    }
}
