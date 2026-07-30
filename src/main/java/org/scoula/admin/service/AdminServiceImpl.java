package org.scoula.admin.service;

import lombok.RequiredArgsConstructor;
import org.scoula.admin.domain.SyncLogVO;
import org.scoula.admin.dto.SyncResultResDto;
import org.scoula.admin.mapper.AdminMapper;
import org.scoula.benefit.dto.YouthPolicyRequestDTO;
import org.scoula.benefit.service.BenefitService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final BenefitService benefitService;   // 예림님 동기화 로직
    private final AdminMapper adminMapper;

    // sync_log 코드값 (DB ENUM과 다르면 이 값만 수정)
    private static final String EXEC_TYPE_MANUAL = "M";   // 관리자 수동 실행
    private static final String EXEC_TYPE_PERIOD = "M";   // 기간별도 관리자가 실행하므로 수동
    // ※ 'P'를 쓰려면 sync_log.exec_type ENUM에 'P'가 추가돼 있어야 한다.
    //    ALTER TABLE 을 안 했다면 이 값을 "M" 으로 바꿔서 쓸 것.

    private static final String STATUS_SUCCESS = "S";
    private static final String STATUS_PARTIAL = "P";
    private static final String STATUS_FAIL = "F";

    // error_msg 컬럼이 varchar(500)이라 초과분은 잘라서 저장한다
    private static final int ERROR_MSG_MAX = 500;

    /**
     * admin-01: 관리자 수동 동기화 (페이지 범위)
     * 예림님 syncYouthPolicies를 그대로 호출하고, 실행 이력만 sync_log에 기록한다.
     *
     * syncYouthPolicies는 처리 건수(int)만 돌려주므로 정확한 UPDATE 건수를 알 수 없다.
     * 동기화 전후 benefit 전체 건수의 차이로 신규 건수를 '추정'한다.
     * 따라서 insertCnt·updateCnt는 정확한 값이 아니라 추정치다.
     */
    @Override
    public SyncResultResDto executeSync(Integer pageNum, Integer pageSize, Integer memberNo) {
        long startTime = System.currentTimeMillis();

        try {
            // 건수 조회도 DB 예외가 날 수 있으므로 try 안에 둔다
            int beforeCount = adminMapper.countBenefits();

            YouthPolicyRequestDTO requestDTO = new YouthPolicyRequestDTO();
            requestDTO.setPageNum(pageNum);
            requestDTO.setPageSize(pageSize);
            requestDTO.setRtnType("json");

            int totalCnt = benefitService.syncYouthPolicies(requestDTO);
            int afterCount = adminMapper.countBenefits();

            int insertCnt = estimateInsertCnt(beforeCount, afterCount, totalCnt);
            int updateCnt = Math.max(0, totalCnt - insertCnt);
            int durationMs = (int) (System.currentTimeMillis() - startTime);

            saveSyncLog(EXEC_TYPE_MANUAL, STATUS_SUCCESS, totalCnt, insertCnt, updateCnt,
                    null, durationMs, memberNo);

            return new SyncResultResDto(
                    STATUS_SUCCESS,
                    "동기화가 완료되었습니다.",
                    totalCnt, insertCnt, updateCnt, durationMs, null);

        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            String errorMsg = normalizeErrorMsg(e);

            saveSyncLog(EXEC_TYPE_MANUAL, STATUS_FAIL, 0, 0, 0, errorMsg, durationMs, memberNo);

            return new SyncResultResDto(
                    STATUS_FAIL,
                    "동기화 중 오류가 발생했습니다.",
                    0, 0, 0, durationMs, errorMsg);
        }
    }

    /**
     * admin-01: 관리자 기간별 동기화 (정책 최초등록일 frst_reg_dt 기준)
     *
     * 주의 1. 기준은 정책이 온통청년에 '등록된 날'이며 신청 기간이 아니다.
     * 주의 2. 온통청년 API가 등록일 조회 파라미터를 제공하지 않아
     *         전체를 받아온 뒤 클라이언트에서 걸러낸다. 기간을 좁혀도 소요 시간은 줄지 않는다.
     * 주의 3. syncYouthPoliciesByFrstRegDt는 API 호출·파싱이 실패해도 예외를 던지지 않고
     *         그때까지의 건수를 반환한다. 그래서 아래 catch만으로는 실패를 잡을 수 없어
     *         0건일 때를 부분 성공(P)으로 남긴다.
     */
    @Override
    public SyncResultResDto executeSyncByPeriod(String startDate, String endDate, Integer memberNo) {
        long startTime = System.currentTimeMillis();

        try {
            int beforeCount = adminMapper.countBenefits();

            // 날짜 형식이 잘못되면 여기서 IllegalArgumentException이 올라온다
            int totalCnt = benefitService.syncYouthPoliciesByFrstRegDt(startDate, endDate);

            int afterCount = adminMapper.countBenefits();

            int insertCnt = estimateInsertCnt(beforeCount, afterCount, totalCnt);
            int updateCnt = Math.max(0, totalCnt - insertCnt);
            int durationMs = (int) (System.currentTimeMillis() - startTime);

            // 0건이면 '해당 기간에 정책이 없었는지'와 'API가 중단됐는지'를 구분할 수 없다.
            // 성공이라고 단정하지 않고 사실만 남긴다.
            if (totalCnt == 0) {
                String partialMsg = "처리된 정책이 0건입니다. "
                        + "해당 기간에 등록된 정책이 없거나 외부 API 호출이 중단됐을 수 있습니다. "
                        + "(기간 " + startDate + " ~ " + endDate + ")";

                saveSyncLog(EXEC_TYPE_PERIOD, STATUS_PARTIAL, 0, 0, 0,
                        partialMsg, durationMs, memberNo);

                return new SyncResultResDto(
                        STATUS_PARTIAL,
                        "처리된 정책이 없습니다. 기간과 서버 로그를 확인해주세요.",
                        0, 0, 0, durationMs, partialMsg);
            }

            saveSyncLog(EXEC_TYPE_PERIOD, STATUS_SUCCESS, totalCnt, insertCnt, updateCnt,
                    null, durationMs, memberNo);

            return new SyncResultResDto(
                    STATUS_SUCCESS,
                    "기간별 동기화가 완료되었습니다.",
                    totalCnt, insertCnt, updateCnt, durationMs, null);

        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            String errorMsg = normalizeErrorMsg(e);

            saveSyncLog(EXEC_TYPE_PERIOD, STATUS_FAIL, 0, 0, 0, errorMsg, durationMs, memberNo);

            return new SyncResultResDto(
                    STATUS_FAIL,
                    "기간별 동기화 중 오류가 발생했습니다.",
                    0, 0, 0, durationMs, errorMsg);
        }
    }

    /**
     * 신규 건수 추정.
     * sync_log에 (insert_cnt + update_cnt + skip_cnt) <= total_cnt CHECK 제약이 있는데,
     * 동기화 도중 스케줄러가 함께 돌면 afterCount - beforeCount 가 totalCnt를 넘을 수 있다.
     * 그 경우 updateCnt가 0이 되면서 CHECK를 위반하므로 totalCnt로 상한을 둔다.
     */
    private int estimateInsertCnt(int beforeCount, int afterCount, int totalCnt) {
        int diff = Math.max(0, afterCount - beforeCount);
        return Math.min(diff, Math.max(0, totalCnt));
    }

    /**
     * 예외 메시지를 sync_log에 넣을 수 있는 형태로 정리한다.
     * NullPointerException처럼 메시지가 없는 예외도 있어 최소한 예외 종류는 남긴다.
     */
    private String normalizeErrorMsg(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = e.getClass().getSimpleName();
        } else {
            message = e.getClass().getSimpleName() + ": " + message;
        }
        return truncate(message);
    }

    /** error_msg가 varchar(500)이라 어떤 경로로 들어오든 길이를 보장한다 */
    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > ERROR_MSG_MAX
                ? message.substring(0, ERROR_MSG_MAX)
                : message;
    }

    /** 로그 기록 실패가 동기화 자체를 실패로 만들지 않도록 분리 */
    private void saveSyncLog(String execType, String resultStatus, int totalCnt, int insertCnt,
                             int updateCnt, String errorMsg, int durationMs, Integer memberNo) {
        try {
            SyncLogVO log = new SyncLogVO();
            log.setExecType(execType);
            log.setResultStatus(resultStatus);
            log.setTotalCnt(totalCnt);
            log.setInsertCnt(insertCnt);
            log.setUpdateCnt(updateCnt);
            log.setSkipCnt(0);          // upsert 방식이라 건너뛰는 건이 없다
            log.setErrorMsg(truncate(errorMsg));
            log.setDurationMs(durationMs);
            log.setMemberNo(memberNo);

            adminMapper.insertSyncLog(log);
        } catch (Exception e) {
            // 로그가 안 남는 것이 가장 위험하므로 원인을 반드시 출력한다
            System.out.println("동기화 이력 기록 실패: " + normalizeErrorMsg(e));
        }
    }
}