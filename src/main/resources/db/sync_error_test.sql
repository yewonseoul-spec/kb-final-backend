-- 동기화 오류 메시지 한글 표시 확인용 테스트 데이터

/*
================================================================================
 파일명   : sync_error_test.sql
 작성자   : 박상호 (admin)
 최종수정 : 2026-08-07 (v1)
--------------------------------------------------------------------------------
 목적
   관리자 화면의 동기화 오류 메시지가 한글로 잘 바뀌는지 확인한다.

   실제로 동기화를 실패시키려면 네트워크를 끊거나 DB를 건드려야 해서
   되돌리기가 번거롭다. 로그만 직접 넣어 화면 표시를 확인한다.

 확인 위치
   관리자 → 동기화 로그  (첫 페이지 위쪽에 뜬다)
   관리자 → 대시보드 → 동기화 로그 (최근 5건)

 주의
   - 실패 로그가 6건 늘어나므로 통계 카드의 '실패' 숫자가 커진다.
   - 확인이 끝나면 파일 아래의 삭제 문장을 반드시 실행할 것.
   - duration_ms = 9999 는 나중에 찾아 지우려고 붙인 표식이다.
================================================================================
*/

USE youthtapa;


-- ==================================================================
-- 1. 테스트 로그 6건 삽입
-- ==================================================================

INSERT INTO sync_log
(executed_at, exec_type, sync_start_date, sync_end_date, result_status,
 total_cnt, insert_cnt, update_cnt, skip_cnt, error_msg, duration_ms, member_no)
VALUES

-- (1) DB 필수값 누락 → "필수 값이 비어 있어 저장하지 못했습니다 (detail_category_code)"
--     실제로 겪은 오류다. 중분류 컬럼이 NOT NULL이라 동기화가 전부 실패했다.
(NOW(), 'M', NULL, NULL, 'F', 0, 0, 0, 0,
 'DataIntegrityViolationException: ### Error updating database.  Cause: java.sql.SQLIntegrityConstraintViolationException: Column ''detail_category_code'' cannot be null ### The error may involve org.scoula.benefit.mapper.BenefitMapper.upsertBenefit-Inline',
 9999, 1),

-- (2) 스키마 불일치 → "DB에 없는 항목을 사용했습니다. 스키마가 최신인지 확인하세요 (mclsf_nm)"
(NOW(), 'M', NULL, NULL, 'F', 0, 0, 0, 0,
 'BadSqlGrammarException: ### Error querying database.  Cause: java.sql.SQLSyntaxErrorException: Unknown column ''mclsf_nm'' in ''field list''',
 9999, 1),

-- (3) 외부 서버가 HTML 오류 페이지를 반환 → "온통청년 서버에서 오류가 발생했습니다 (HTTP 500)"
--     원문이 길어 '원문 보기'를 눌렀을 때 스크롤이 잘 되는지 함께 확인한다.
(NOW(), 'A', NULL, NULL, 'F', 0, 0, 0, 0,
 'InternalServerError: 500 Internal Server Error: <!DOCTYPE html><html><head><title>500 Internal Server Error</title></head><body><h1>Internal Server Error</h1><p>The server encountered an unexpected condition that prevented it from fulfilling the request.</p><hr><address>Apache Server at www.youthcenter.go.kr</address></body></html>',
 9999, NULL),

-- (4) 응답 시간 초과 → "응답을 기다리다 시간이 초과되었습니다"
--     일부는 처리된 상태라 PARTIAL로 넣는다. 노란 글씨로 뜨는지 확인한다.
(NOW(), 'A', NULL, NULL, 'P', 1200, 30, 400, 770,
 'ResourceAccessException: I/O error on GET request for "https://www.youthcenter.go.kr/go/ythip/getPlcy": Read timed out; nested exception is java.net.SocketTimeoutException: Read timed out',
 9999, NULL),

-- (5) 응답 형식 오류 → "응답 형식을 해석하지 못했습니다"
(NOW(), 'M', '2026-07-01', '2026-08-07', 'F', 0, 0, 0, 0,
 'JsonParseException: Unexpected character (''<'' (code 60)): expected a valid value at [Source: (String)"<html><body>Service Unavailable</body></html>"; line: 1, column: 2]',
 9999, 1),

-- (6) 규칙에 없는 예외 → "알 수 없는 오류가 발생했습니다"
--     대응표에 없는 오류가 와도 화면이 깨지지 않는지 확인한다.
(NOW(), 'M', NULL, NULL, 'F', 0, 0, 0, 0,
 'SomeUnexpectedException: 처음 보는 형태의 오류입니다 xyz-9182',
 9999, 1);


-- ==================================================================
-- 2. 화면에서 확인할 것
-- ==================================================================
/*
   관리자 → 동기화 로그

   [1] 오류 내용 칸에 한글 요약이 뜨는가
   [2] '오류' 버튼(빨간 테두리)이 눌리는가
       - 실패 로그는 처리 건수가 0이라 예전에는 버튼이 회색이었다.
         오류가 있으면 열리도록 바꿨다.
   [3] 모달에서 '원문 보기'를 누르면 원문이 나오는가
   [4] (3)번 로그처럼 긴 HTML도 회색 박스 안에서만 스크롤되는가
   [5] 대시보드 최근 5건에도 한글 요약이 뜨는가

   시드 데이터로 들어 있던 '공공데이터포털 API 응답 오류(HTTP 500)' 같은
   메시지는 이미 한글이라 바뀌지 않는 것이 정상이다. '원문 보기'도 안 뜬다.
*/


-- ==================================================================
-- 3. 확인이 끝나면 삭제
-- ==================================================================

DELETE FROM sync_log WHERE duration_ms = 9999;

-- 남아 있는지 확인
SELECT COUNT(*) AS 남은_테스트로그 FROM sync_log WHERE duration_ms = 9999;   -- 0
