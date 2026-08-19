package org.scoula.admin.service;

import lombok.RequiredArgsConstructor;
import org.scoula.admin.domain.SyncLogDetailVO;
import org.scoula.admin.domain.SyncLogVO;
import org.scoula.admin.dto.*;
import org.scoula.admin.mapper.AdminMapper;
import org.scoula.benefit.dto.SyncDetailResultDTO;
import org.scoula.benefit.dto.SyncedBenefitDTO;
import org.scoula.benefit.dto.YouthPolicyRequestDTO;
import org.scoula.benefit.service.BenefitService;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final BenefitService benefitService;   // 예림님 동기화 로직
    private final AdminMapper adminMapper;

    // sync_log 코드값 (DB ENUM과 다르면 이 값만 수정)
    private static final String EXEC_TYPE_MANUAL = "M";   // 관리자 수동 실행
    private static final String EXEC_TYPE_PERIOD = "M";   // 기간별도 관리자가 실행하므로 수동
    // ※ exec_type은 '누가 실행했나'(자동/수동)를 나타낸다.
    //    페이지 범위냐 기간 범위냐는 '어떻게 실행했나'라는 다른 축이므로 여기서 구분하지 않고,
    //    대신 sync_start_date·sync_end_date에 실제 대상 기간을 남긴다.

    private static final String STATUS_SUCCESS = "S";
    private static final String STATUS_PARTIAL = "P";
    private static final String STATUS_FAIL = "F";

    // error_msg 컬럼이 varchar(500)이라 초과분은 잘라서 저장한다
    private static final int ERROR_MSG_MAX = 500;

    // 대시보드 목록(최근 동기화·마감 임박)에 보여줄 건수
    private static final int DASHBOARD_LIST_SIZE = 5;

    // 로그 목록 페이지 크기 상·하한 (잘못된 값이 들어와도 쿼리가 깨지지 않게 한다)
    private static final int PAGE_SIZE_DEFAULT = 20;
    private static final int PAGE_SIZE_MAX = 100;

    // 혜택 목록 페이지 크기
    private static final int BENEFIT_PAGE_SIZE_DEFAULT = 20;
    private static final int BENEFIT_PAGE_SIZE_MAX = 100;

    // 컬럼은 TEXT라 여유가 있지만, 이보다 긴 값은 사람이 붙여넣은 링크가 아니라
    // 잘못 들어온 데이터일 가능성이 높아 입력 단계에서 막는다
    private static final int CUSTOM_APPLY_URL_MAX_LENGTH = 500;


    /**
     * admin-01: 관리자 대시보드 운영 현황
     *
     * '전체 정책'과 '추천 가능 정책'을 나눠 보여주는 것이 핵심이다.
     * 전체 건수만으로는 실제 추천에 쓰이는 정책이 몇 건인지 알 수 없다.
     * (마감됐거나 아직 신청 시작 전인 정책이 is_active='N'으로 빠져 있다)
     */
    @Override
    public DashboardResDto getDashboard() {
        return DashboardResDto.builder()
                .totalBenefits(adminMapper.countBenefits())
                .activeBenefits(adminMapper.countActiveBenefits())
                .deadlineSoonCount(adminMapper.countDeadlineSoon())
                .conflictRuleCount(adminMapper.countActiveConflictRules())
                .memberCount(adminMapper.countMembers())
                .recentSyncLogs(adminMapper.findRecentSyncLogs(DASHBOARD_LIST_SIZE))
                .deadlineBenefits(adminMapper.findDeadlineSoonBenefits(DASHBOARD_LIST_SIZE))
                .build();
    }


    /**
     * admin-03: 동기화 로그 목록 조회
     *
     * 통계 카드는 상태·유형 필터를 빼고 기간만 반영한다.
     * SUCCESS만 걸러놓고 '성공 41회 실패 0회'를 보여주면 정보가 사라지기 때문이다.
     */
    @Override
    public SyncLogPageResDto getSyncLogs(SyncLogSearchReqDto search) {
        normalizePaging(search);

        int totalCount = adminMapper.countSyncLogs(search);
        List<SyncLogVO> logs = adminMapper.findSyncLogs(search);
        SyncLogStatsResDto stats = adminMapper.findSyncLogStats(search);

        int size = search.getSize();
        int totalPages = (totalCount == 0) ? 0 : ((totalCount - 1) / size) + 1;

        return SyncLogPageResDto.builder()
                .page(search.getPage())
                .size(size)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .stats(stats)
                .logs(logs)
                .build();
    }

    /**
     * admin-03: 동기화 갱신 내역 조회
     *
     * 로그에는 '몇 건 처리했다'는 집계만 남아 어떤 혜택이 갱신됐는지 알 수 없다.
     * 관리자가 실제로 무엇이 들어왔는지 확인할 수 있게 건별 내역을 돌려준다.
     */
    @Override
    public List<SyncLogDetailResDto> getSyncLogDetails(int logNo) {
        return adminMapper.findSyncLogDetails(logNo);
    }

    /** 페이지 값이 비어 있거나 범위를 벗어나도 쿼리가 깨지지 않도록 보정한다 */
    private void normalizePaging(SyncLogSearchReqDto search) {
        if (search.getPage() == null || search.getPage() < 1) {
            search.setPage(1);
        }
        if (search.getSize() == null || search.getSize() < 1) {
            search.setSize(PAGE_SIZE_DEFAULT);
        }
        if (search.getSize() > PAGE_SIZE_MAX) {
            search.setSize(PAGE_SIZE_MAX);
        }
        search.setOffset((search.getPage() - 1) * search.getSize());
    }


    /**
     * admin-02: 혜택 목록 조회
     */
    @Override
    public AdminBenefitPageResDto getBenefits(AdminBenefitSearchReqDto search) {
        normalizeBenefitPaging(search);

        int totalCount = adminMapper.countBenefitList(search);
        List<AdminBenefitListResDto> benefits = adminMapper.findBenefitList(search);

        int size = search.getSize();
        int totalPages = (totalCount == 0) ? 0 : ((totalCount - 1) / size) + 1;

        return AdminBenefitPageResDto.builder()
                .page(search.getPage())
                .size(size)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .benefits(benefits)
                .build();
    }

    /**
     * admin-02: 혜택 상세 조회
     */
    @Override
    public AdminBenefitDetailResDto getBenefitDetail(int benefitNo) {
        AdminBenefitDetailResDto detail = adminMapper.findBenefitDetail(benefitNo);
        if (detail == null) {
            throw new IllegalArgumentException("존재하지 않는 혜택입니다. benefitNo=" + benefitNo);
        }
        return detail;
    }

    /**
     * 혜택 노출 상태를 바꾼다.
     *
     * 원본 is_active 를 직접 고치지 않고 관리자 지정 컬럼에 저장한다.
     * 원본은 동기화가 매번 덮어쓰기 때문에 거기에 쓰면 관리자가 누른 행위가
     * 다음 동기화에서 사라진다.
     *
     * isActive 에 null 을 주면 지정을 해제하고 다시 API 원본을 따른다.
     */
    /**
     * 혜택 노출 상태를 바꾼다.
     *
     * 원본 is_active 를 직접 고치지 않고 관리자 지정 컬럼에 저장한다.
     * 원본은 동기화의 ON DUPLICATE KEY UPDATE 목록에 있어 매번 덮어쓰이므로,
     * 거기에 쓰면 관리자가 누른 행위가 다음 동기화에서 사라진다.
     * custom_apply_url 과 같은 구조다.
     *
     * isActive 가 null 이거나 빈 값이면 지정을 해제하고 다시 API 원본을 따른다.
     */
    @Override
    public AdminBenefitDetailResDto changeBenefitActive(int benefitNo, String isActive) {

        // 화면에서 빈 문자열이 올 수 있다. 해제와 같은 뜻으로 본다
        String value = (isActive == null || isActive.trim().isEmpty())
                ? null : isActive.trim();

        if (value != null && !"Y".equals(value) && !"N".equals(value)) {
            throw new IllegalArgumentException("활성 상태는 Y 또는 N 이어야 합니다: " + isActive);
        }

        adminMapper.updateAdminActive(benefitNo, value);

        // 바뀐 뒤의 상태를 다시 읽어 내려준다.
        // 최종 노출 상태는 세 컬럼을 합쳐 만들어지므로 화면이 직접 계산할 수 없다
        return adminMapper.findBenefitDetail(benefitNo);
    }

    /**
     * admin-02: 관리자 지정 신청 URL 저장·해제
     *
     * 원본(aply_url_addr)은 건드리지 않는다. 동기화의 upsert는 이 컬럼을 모르므로
     * 지정값이 덮이지 않고, 해제하면 다시 원본이 쓰이므로 언제든 되돌릴 수 있다.
     *
     * 형식을 검사하는 이유는 원본 데이터가 그렇지 않기 때문이다.
     * aply_url_addr에는 '전화문의', '-', 스킴 없는 'www.…' 같은 값이 섞여 있는데,
     * 관리자가 지정하는 값까지 그러면 사용자 화면에서 같은 문제가 반복된다.
     * 여기서 막으면 지정값은 항상 열리는 주소임이 보장된다.
     */
    @Override
    public AdminBenefitDetailResDto changeCustomApplyUrl(int benefitNo, String customApplyUrl) {
        String value = (customApplyUrl == null) ? null : customApplyUrl.trim();

        // 빈 값은 '해제'로 본다. 매퍼의 NULLIF와 의미를 맞춘다
        if (value != null && !value.isEmpty()) {
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                throw new IllegalArgumentException(
                        "신청 URL은 http:// 또는 https:// 로 시작해야 합니다. 입력값=" + value);
            }
            if (value.length() > CUSTOM_APPLY_URL_MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "신청 URL이 너무 깁니다. " + CUSTOM_APPLY_URL_MAX_LENGTH + "자 이내로 입력해 주세요.");
            }
        }

        int updated = adminMapper.updateCustomApplyUrl(benefitNo, value);
        if (updated == 0) {
            throw new IllegalArgumentException("존재하지 않는 혜택입니다. benefitNo=" + benefitNo);
        }

        return adminMapper.findBenefitDetail(benefitNo);
    }

    /** 페이지 값이 비어 있거나 범위를 벗어나도 쿼리가 깨지지 않도록 보정한다 */
    private void normalizeBenefitPaging(AdminBenefitSearchReqDto search) {
        if (search.getPage() == null || search.getPage() < 1) {
            search.setPage(1);
        }
        if (search.getSize() == null || search.getSize() < 1) {
            search.setSize(BENEFIT_PAGE_SIZE_DEFAULT);
        }
        if (search.getSize() > BENEFIT_PAGE_SIZE_MAX) {
            search.setSize(BENEFIT_PAGE_SIZE_MAX);
        }
        search.setOffset((search.getPage() - 1) * search.getSize());
    }


    /**
     * admin-01: 관리자 수동 동기화 (페이지 범위)
     * 예림님 syncYouthPolicies를 그대로 호출하고, 실행 이력만 sync_log에 기록한다.
     *
     * syncYouthPolicies는 처리 건수(int)만 돌려주므로 정확한 UPDATE 건수를 알 수 없다.
     * 동기화 전후 benefit 전체 건수의 차이로 신규 건수를 '추정'한다.
     * 따라서 insertCnt·updateCnt는 정확한 값이 아니라 추정치다.
     *
     * 페이지 범위 방식은 관리자 화면에서 제거했고 개발 확인용으로만 남겨둔다.
     * 대상 기간과 처리 내역이 없으므로 상세는 기록하지 않는다.
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

            saveSyncLog(EXEC_TYPE_MANUAL, null, null, STATUS_SUCCESS,
                    totalCnt, insertCnt, updateCnt, null, durationMs, memberNo, null);

            return new SyncResultResDto(
                    STATUS_SUCCESS,
                    "동기화가 완료되었습니다.",
                    totalCnt, insertCnt, updateCnt, durationMs, null);

        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            String errorMsg = normalizeErrorMsg(e);

            saveSyncLog(EXEC_TYPE_MANUAL, null, null, STATUS_FAIL,
                    0, 0, 0, errorMsg, durationMs, memberNo, null);

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
     * 주의 3. 동기화 메서드는 API 호출·파싱이 실패해도 예외를 던지지 않고
     *         그때까지의 결과를 반환한다. 그래서 아래 catch만으로는 실패를 잡을 수 없어
     *         0건일 때를 부분 성공(P)으로 남긴다.
     *
     * 어떤 기간을 대상으로 무엇을 처리했는지 알 수 없으면 이력을 나중에 해석할 수 없으므로
     * 대상 기간과 처리한 혜택 목록을 함께 기록한다.
     */
    @Override
    public SyncResultResDto executeSyncByPeriod(String startDate, String endDate, Integer memberNo) {
        long startTime = System.currentTimeMillis();

        try {
            int beforeCount = adminMapper.countBenefits();

            // 처리 내역까지 함께 받아 sync_log_detail에 기록한다
            SyncDetailResultDTO syncResult =
                    benefitService.syncByFrstRegDtWithDetail(startDate, endDate);

            int totalCnt = syncResult.getTotalCount();
            int afterCount = adminMapper.countBenefits();

            int insertCnt = estimateInsertCnt(beforeCount, afterCount, totalCnt);
            int updateCnt = Math.max(0, totalCnt - insertCnt);
            int durationMs = (int) (System.currentTimeMillis() - startTime);

            // 0건이면 '해당 기간에 정책이 없었는지'와 'API가 중단됐는지'를 구분할 수 없다.
            // 성공이라고 단정하지 않고 사실만 남긴다.
            if (totalCnt == 0) {
                String partialMsg = "처리된 정책이 0건입니다. "
                        + "해당 기간에 등록된 정책이 없거나 외부 API 호출이 중단됐을 수 있습니다.";

                saveSyncLog(EXEC_TYPE_PERIOD, startDate, endDate, STATUS_PARTIAL,
                        0, 0, 0, partialMsg, durationMs, memberNo, null);

                return new SyncResultResDto(
                        STATUS_PARTIAL,
                        "처리된 정책이 없습니다. 기간과 서버 로그를 확인해주세요.",
                        0, 0, 0, durationMs, partialMsg);
            }

            saveSyncLog(EXEC_TYPE_PERIOD, startDate, endDate, STATUS_SUCCESS,
                    totalCnt, insertCnt, updateCnt, null, durationMs, memberNo,
                    syncResult.getItems());

            return new SyncResultResDto(
                    STATUS_SUCCESS,
                    "기간별 동기화가 완료되었습니다.",
                    totalCnt, insertCnt, updateCnt, durationMs, null);

        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            String errorMsg = normalizeErrorMsg(e);

            saveSyncLog(EXEC_TYPE_PERIOD, startDate, endDate, STATUS_FAIL,
                    0, 0, 0, errorMsg, durationMs, memberNo, null);

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

    /**
     * 화면에서 넘어오는 yyyy-MM-dd를 DATE로 바꾼다.
     * 페이지 범위 동기화처럼 대상 기간이 없는 경우는 null이다.
     * 형식이 잘못돼도 이력 기록 자체가 실패하면 안 되므로 null로 넘긴다.
     */
    private Date parseDate(String yyyyMMdd) {
        if (yyyyMMdd == null || yyyyMMdd.trim().isEmpty()) {
            return null;
        }
        try {
            return Date.valueOf(yyyyMMdd.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 로그 기록 실패가 동기화 자체를 실패로 만들지 않도록 분리 */
    private void saveSyncLog(String execType, String startDate, String endDate,
                             String resultStatus, int totalCnt, int insertCnt,
                             int updateCnt, String errorMsg, int durationMs, Integer memberNo,
                             List<SyncedBenefitDTO> syncedItems) {
        try {
            SyncLogVO log = new SyncLogVO();
            log.setExecType(execType);
            log.setSyncStartDate(parseDate(startDate));
            log.setSyncEndDate(parseDate(endDate));
            log.setResultStatus(resultStatus);
            log.setTotalCnt(totalCnt);
            log.setInsertCnt(insertCnt);
            log.setUpdateCnt(updateCnt);
            log.setSkipCnt(0);          // upsert 방식이라 건너뛰는 건이 없다
            log.setDeleteCnt(0);
            log.setErrorMsg(truncate(errorMsg));
            log.setDurationMs(durationMs);
            log.setMemberNo(memberNo);

            // useGeneratedKeys 로 log.logNo 가 채워진다
            adminMapper.insertSyncLog(log);

            saveSyncLogDetails(log.getLogNo(), syncedItems);

        } catch (Exception e) {
            // 로그가 안 남는 것이 가장 위험하므로 원인을 반드시 출력한다
            System.out.println("동기화 이력 기록 실패: " + normalizeErrorMsg(e));
        }
    }

    /**
     * 처리 내역 저장.
     * 상세가 없어도 동기화 자체는 성공이므로 실패해도 예외를 올리지 않는다.
     */
    private void saveSyncLogDetails(Integer logNo, List<SyncedBenefitDTO> items) {
        if (logNo == null || items == null || items.isEmpty()) {
            return;
        }
        try {
            List<SyncLogDetailVO> details = new ArrayList<>(items.size());
            for (SyncedBenefitDTO item : items) {
                details.add(new SyncLogDetailVO(logNo, item.getBenefitNo(),
                        item.getActionType(), item.getChangedSummary()));
            }
            adminMapper.insertSyncLogDetails(details);
        } catch (Exception e) {
            System.out.println("동기화 처리 내역 기록 실패: " + normalizeErrorMsg(e));
        }
    }


    @Override
    public List<RecommendKeywordAdminDTO> getRecommendKeywords() {
        return adminMapper.findRecommendKeywords();
    }

    @Override
    public void createRecommendKeyword(
            String keywordName
    ) {
        if (
                keywordName == null
                        || keywordName.trim().isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "추천검색어를 입력해주세요."
            );
        }

        String normalizedKeyword =
                keywordName.trim();

        int count =
                adminMapper.countRecommendKeywordByName(
                        normalizedKeyword
                );

        if (count > 0) {
            throw new IllegalArgumentException(
                    "이미 등록된 추천검색어입니다."
            );
        }

        adminMapper.insertRecommendKeyword(
                normalizedKeyword
        );
    }

    @Override
    public void changeRecommendKeywordStatus(
            Integer keywordCode,
            String isActive
    ) {
        if (
                !"Y".equals(isActive)
                        && !"N".equals(isActive)
        ) {
            throw new IllegalArgumentException(
                    "활성 상태는 Y 또는 N이어야 합니다."
            );
        }

        adminMapper.updateRecommendKeywordStatus(
                keywordCode,
                isActive
        );
    }

    @Override
    public void deleteRecommendKeyword(
            Integer keywordCode
    ) {
        adminMapper.deleteRecommendKeyword(
                keywordCode
        );
    }
}