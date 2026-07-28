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
    private static final String STATUS_SUCCESS = "S";
    private static final String STATUS_FAIL = "F";

    // error_msg 컬럼이 varchar(500)이라 초과분은 잘라서 저장한다
    private static final int ERROR_MSG_MAX = 500;

    /**
     * admin-01: 관리자 수동 동기화
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

            // sync_log에 각 카운트 >= 0 CHECK 제약이 있어 음수를 막는다
            int insertCnt = Math.max(0, afterCount - beforeCount);
            int updateCnt = Math.max(0, totalCnt - insertCnt);
            int durationMs = (int) (System.currentTimeMillis() - startTime);

            saveSyncLog(STATUS_SUCCESS, totalCnt, insertCnt, updateCnt,
                    null, durationMs, memberNo);

            return new SyncResultResDto(
                    STATUS_SUCCESS,
                    "동기화가 완료되었습니다.",
                    totalCnt, insertCnt, updateCnt, durationMs, null);

        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            String errorMsg = normalizeErrorMsg(e);

            saveSyncLog(STATUS_FAIL, 0, 0, 0, errorMsg, durationMs, memberNo);

            return new SyncResultResDto(
                    STATUS_FAIL,
                    "동기화 중 오류가 발생했습니다.",
                    0, 0, 0, durationMs, errorMsg);
        }
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
        return message.length() > ERROR_MSG_MAX
                ? message.substring(0, ERROR_MSG_MAX)
                : message;
    }

    /** 로그 기록 실패가 동기화 자체를 실패로 만들지 않도록 분리 */
    private void saveSyncLog(String resultStatus, int totalCnt, int insertCnt,
                             int updateCnt, String errorMsg, int durationMs, Integer memberNo) {
        try {
            SyncLogVO log = new SyncLogVO();
            log.setExecType(EXEC_TYPE_MANUAL);
            log.setResultStatus(resultStatus);
            log.setTotalCnt(totalCnt);
            log.setInsertCnt(insertCnt);
            log.setUpdateCnt(updateCnt);
            log.setSkipCnt(0);          // upsert 방식이라 건너뛰는 건이 없다
            log.setErrorMsg(errorMsg);
            log.setDurationMs(durationMs);
            log.setMemberNo(memberNo);

            adminMapper.insertSyncLog(log);
        } catch (Exception e) {
            // 로그가 안 남는 것이 가장 위험하므로 원인을 반드시 출력한다
            System.out.println("동기화 이력 기록 실패: " + normalizeErrorMsg(e));
        }
    }
}
