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

    // sync_log 코드값 (DB CHECK 제약과 다르면 이 값만 수정)
    private static final String EXEC_TYPE_MANUAL = "M";   // 관리자 수동 실행
    private static final String STATUS_SUCCESS = "S";
    private static final String STATUS_FAIL = "F";

    /**
     * admin-01: 관리자 수동 동기화
     * 예림님 syncYouthPolicies를 그대로 호출하고, 실행 이력만 sync_log에 기록한다.
     * syncYouthPolicies는 처리 건수(int)만 돌려주므로
     * 동기화 전후 benefit 건수 차이로 신규 건수를 계산한다.
     */
    @Override
    public SyncResultResDto executeSync(Integer pageNum, Integer pageSize, Integer memberNo) {
        long startTime = System.currentTimeMillis();

        int beforeCount = adminMapper.countBenefits();

        YouthPolicyRequestDTO requestDTO = new YouthPolicyRequestDTO();
        requestDTO.setPageNum(pageNum);
        requestDTO.setPageSize(pageSize);
        requestDTO.setRtnType("json");

        try {
            int totalCnt = benefitService.syncYouthPolicies(requestDTO);

            int afterCount = adminMapper.countBenefits();
            int insertCnt = afterCount - beforeCount;      // 행이 늘어난 만큼이 신규
            int updateCnt = totalCnt - insertCnt;          // 나머지는 기존 정책 갱신
            int durationMs = (int) (System.currentTimeMillis() - startTime);

            saveSyncLog(STATUS_SUCCESS, totalCnt, insertCnt, updateCnt,
                    null, durationMs, memberNo);

            return new SyncResultResDto(
                    STATUS_SUCCESS,
                    "동기화가 완료되었습니다.",
                    totalCnt, insertCnt, updateCnt, durationMs, null);

        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            String errorMsg = e.getMessage();

            saveSyncLog(STATUS_FAIL, 0, 0, 0, errorMsg, durationMs, memberNo);

            return new SyncResultResDto(
                    STATUS_FAIL,
                    "동기화 중 오류가 발생했습니다.",
                    0, 0, 0, durationMs, errorMsg);
        }
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
            System.out.println("동기화 이력 기록 실패: " + e.getMessage());
        }
    }
}
