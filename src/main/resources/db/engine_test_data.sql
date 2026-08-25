-- engine 중복수혜 검사 시연·검증용 테스트 데이터 (회원 2번 기준)

/*
================================================================================
 파일명   : engine_test_data.sql
 작성자   : 박상호 (engine)
 최종수정 : 2026-08-06 (v2.2)
 실행순서 : schema.sql → data_0_code.sql → data_1_base.sql → 혜택 동기화 → 이 파일
--------------------------------------------------------------------------------
 목적
   온통청년 OPEN API는 정책 간 중복수혜 관계를 구조화된 데이터로 제공하지 않는다.
   benefit.conflict_group_code 와 benefit_conflict_rule 은 적재 직후 전부 비어 있고,
   실제 운영에서는 관리자가 공고문을 검수해 직접 등록하는 구조로 설계했다.

   이 파일은 그 구조가 실제로 동작하는지 확인하기 위한 시연·검증용 데이터다.

 ★ 규칙 선정 기준 (임의로 짝지은 것이 아님)
   모든 규칙은 benefit.plcy_sprt_cn(지원 내용)과 sprvsn_inst_cd_nm(주관기관)을
   실제로 읽고, 아래 중 하나 이상에 해당하는 쌍만 등록했다.
     (a) 같은 기관·같은 부서가 운영하는 같은 목적의 자금 지원
     (b) 물리적으로 동시 이용이 불가능한 서비스 (예: 주거공간 입주)
     (c) 지원 항목이 실질적으로 겹치는 사업 (예: 취업 컨설팅 + 직무교육)

   다만 이 규칙들이 각 기관이 실제로 고시한 중복수혜 규정임을 확인한 것은 아니다.
   발표·문서에서 실제 규정처럼 인용하면 안 된다.
   그래서 일부 규칙은 confirm_status='검수필요' 로 남겨 관리자 검수 대기 상태를 재현한다.

 ★ 충돌 유형별 처리
   중복불가   후보 또는 조합에서 제거
   일부제한   유지 + 경고 + 추천점수 20점 미부여
   확인필요   유지 + 경고 + 추천점수 20점 미부여
   외부 제도  유지 + 경고, 점수에 영향 없음

   추천점수의 '중복수혜' 항목은 '내부 경고가 하나도 없을 때만 20점'이다.
   따라서 일부제한이든 확인필요든 내부 경고가 붙으면 20점을 받지 못한다.
   trigger_benefit_no 가 NULL 인 외부 제도 경고만 점수와 무관하다.

 주의
   - benefit_no 는 환경마다 다를 수 있으므로 plcy_no 로 조회해 변수에 담는다.
   - confirm_status='확정' AND is_active='Y' 인 규칙만 엔진이 적용한다.
   - DB 데이터만 바꾸므로 서버 재시작은 필요 없다.
   - 운영 데이터가 있는 DB에서 실행하지 말 것.
   - 혜택 동기화를 먼저 끝내야 한다. benefit 테이블이 비어 있으면 아무것도 안 들어간다.

--------------------------------------------------------------------------------
 변경 이력
   2026-07-29 v1    충돌 검사 4종 + 예외 처리 2종 최소 구성 (규칙 6건)
   2026-07-29 v2    정책 본문 근거로 규칙 재구성. 그룹 4개·규칙 11건으로 확장.
                    v1의 임의 조합(청년예술인 적립계좌 ↔ 청년키움지원센터,
                    안성시 청년내일캠프 ↔ 청년예술인 적립계좌)은 근거가 없어 제거.
                    보유 정책을 1건 → 2건으로 늘려 그룹형 검사 재현 폭을 넓힘.
   2026-07-31 v2.1  검증 중 확인필요 규칙의 점수 처리가 주석과 다른 것을 발견해 정정.
                    '확인필요 = 감점 없음'은 틀렸고 내부 경고이므로 20점을 받지 못한다.
                    코드는 룰북과 일관하며 주석 쪽이 잘못돼 있었다.
   2026-08-06 v2.2  다른 환경에서 규칙이 0건으로 적재되는 문제 대응.
                    온통청년에서 사라진 정책이 있으면 변수가 NULL 이 되고,
                    그 INSERT 에서 실행이 멈춰 뒤의 규칙이 전부 안 들어갔다.
                    모든 INSERT 에 NULL 방어를 넣어, 못 찾은 정책은 건너뛰고
                    나머지 규칙은 정상 적재되도록 바꿨다.
================================================================================
*/

USE youthtapa;


-- ==================================================================
-- 0. 정책 번호 조회 (plcy_no 는 UNIQUE 라 환경이 달라도 동일하게 찾힌다)
-- ==================================================================

-- [금융]
SET @sunshine = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260724005400113307'); -- 청년 자금지원을 위한 햇살론유스 운영 (금융위원회)
SET @miso     = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260421005400112773'); -- 미소금융 청년 미래이음 대출 (서민금융진흥원)
SET @dream    = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260616005400113238'); -- 청년주택드림청약통장 (국토교통부)
SET @debt     = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260318005400212198'); -- 청년 학자금대출 장기연체자 지원사업 (군포시)

-- [평택시 창업]
SET @startup  = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260326005400212295'); -- 평택시 우수초기창업자 지원사업 (사업화자금 최대 1천만원)
SET @interest = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260326005400212293'); -- 평택시 청년창업 금융지원사업 이차보전 지원 (대출이자 2% 지원)
SET @credit   = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260326005400212292'); -- 평택시 청년창업자 금융지원사업 (보증한도 5천만원)
SET @funding  = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260326005400212291'); -- 평택시 청년창업자를 위한 크라우드 펀딩사업 지원

-- [의성군 주거·공간]
SET @stay1    = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260325005400212276'); -- 청년단기주거공간 운영(금강장)
SET @stay2    = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260325005400212275'); -- 청년복합주거공간 운영(금수장)
SET @kium     = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260330005400212324'); -- 청년키움지원센터 운영
SET @chung    = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260330005400212322'); -- 청춘어람 운영
SET @incu     = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260330005400212325'); -- 청년인큐베이팅공유공간 운영

-- [중개보수 감면]
SET @fee1     = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260326005400212288'); -- 평택시 청년 전월세 중개보수료 감면사업 (20% 감면)
SET @fee2     = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260313005400212147'); -- 용인청년 부동산 중개 보수 감면 사업 (20% 이상 감면)

-- [취업·교육]
SET @future   = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260718005400113261'); -- 청년미래플러스 (고용노동부, 취업 컨설팅·직무교육)
SET @camp     = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260319005400212233'); -- 안성시 청년내일캠프 (취업역량 교육·개별컨설팅)
SET @match    = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260320005400112238'); -- 산업맞춤 단기직무능력인증과정 Match業 (교육부)
SET @ai       = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260319005400212235'); -- 인공지능 전문인력 양성교육 (경기도)
SET @allow    = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260429005400212947'); -- 경기도 청년 면접수당 (1회 5만원, 최대 3회)
SET @suit     = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260326005400212286'); -- 평택시 청년 면접정장 무료대여 서비스
SET @portal   = (SELECT benefit_no FROM benefit WHERE plcy_no = '20260313005400212151'); -- 용인청년포털 청년e랑 운영


-- ------------------------------------------------------------------
-- 진단 : 못 찾은 정책이 있는지 먼저 본다.
--        온통청년에서 내려온 정책 목록은 시점에 따라 달라질 수 있다.
--        benefit_no 가 NULL 인 줄이 있으면 그 정책은 이 DB 에 없다는 뜻이며,
--        해당 정책이 걸린 규칙만 자동으로 건너뛴다. 나머지는 정상 적재된다.
-- ------------------------------------------------------------------
SELECT '햇살론유스' AS 정책, @sunshine AS benefit_no
UNION ALL SELECT '미소금융 청년 미래이음 대출', @miso
UNION ALL SELECT '청년주택드림청약통장',        @dream
UNION ALL SELECT '학자금대출 장기연체자 지원',  @debt
UNION ALL SELECT '평택 우수초기창업자',         @startup
UNION ALL SELECT '평택 이차보전',               @interest
UNION ALL SELECT '평택 청년창업자 금융지원',    @credit
UNION ALL SELECT '평택 크라우드 펀딩',          @funding
UNION ALL SELECT '금강장',                      @stay1
UNION ALL SELECT '금수장',                      @stay2
UNION ALL SELECT '청년키움지원센터',            @kium
UNION ALL SELECT '청춘어람',                    @chung
UNION ALL SELECT '청년인큐베이팅공유공간',      @incu
UNION ALL SELECT '평택 중개보수 감면',          @fee1
UNION ALL SELECT '용인 중개보수 감면',          @fee2
UNION ALL SELECT '청년미래플러스',              @future
UNION ALL SELECT '안성시 청년내일캠프',         @camp
UNION ALL SELECT 'Match業',                     @match
UNION ALL SELECT 'AI 전문인력 양성교육',        @ai
UNION ALL SELECT '경기도 청년 면접수당',        @allow
UNION ALL SELECT '평택 면접정장 대여',          @suit
UNION ALL SELECT '용인청년포털',                @portal;


-- ==================================================================
-- 1. 보유 정책 — 회원 2번이 이미 받고 있는 정책 2건
-- ==================================================================
-- 성격이 다른 두 정책을 보유하게 해서 그룹형 검사가 두 갈래로 걸리는지 본다.
--   햇살론유스     → 서민금융 소액대출 계열
--   우수초기창업자 → 평택시 창업 자금 계열

DELETE FROM applied_benefit WHERE member_no = 2;

INSERT INTO applied_benefit (member_no, benefit_no)
SELECT 2, @sunshine WHERE @sunshine IS NOT NULL;

INSERT INTO applied_benefit (member_no, benefit_no)
SELECT 2, @startup WHERE @startup IS NOT NULL;


-- ==================================================================
-- 2. 그룹형 충돌 (benefit.conflict_group_code)
-- ==================================================================
-- 같은 성격의 정책 묶음에 같은 코드를 붙인다. 한 묶음에서 하나만 받을 수 있다.

UPDATE benefit SET conflict_group_code = NULL WHERE conflict_group_code IS NOT NULL;

-- G01 · 서민금융 청년 소액대출
--   근거: 햇살론유스(금융위)와 미소금융 청년 미래이음 대출(서민금융진흥원) 모두
--         청년 대상 정책서민금융 상품이며 한도 심사가 연계된다.
--   기대: 보유 정책과 같은 그룹 → 미소금융 대출이 후보에서 제거
UPDATE benefit SET conflict_group_code = 'G01' WHERE benefit_no IN (@sunshine, @miso);

-- G02 · 평택시 청년창업 자금 지원
--   근거: 세 사업 모두 경기도 평택시 기획항만경제실이 운영하는 창업 자금 지원이다.
--         사업화자금 / 대출이자 지원 / 보증 지원으로 형태만 다르고 목적이 같다.
--   기대: 보유 정책(우수초기창업자)과 같은 그룹 → 나머지 2건이 후보에서 제거
UPDATE benefit SET conflict_group_code = 'G02' WHERE benefit_no IN (@startup, @interest, @credit);

-- G03 · 의성군 청년 주거공간 입주
--   근거: 금강장·금수장 모두 의성군이 제공하는 숙박형 주거공간이다.
--         한 사람이 두 곳에 동시에 거주할 수 없다.
--   기대: 둘 다 보유 정책이 아니므로 후보에는 남고, 같은 조합에만 못 들어감
UPDATE benefit SET conflict_group_code = 'G03' WHERE benefit_no IN (@stay1, @stay2);

-- G04 · 청년 전월세 중개보수 감면
--   근거: 평택시·용인시 모두 임차계약 1건에 대한 중개보수 감면이다.
--         임차계약은 한 곳에서만 체결하므로 실질적으로 중복이 불가능하다.
--   기대: 둘 다 후보에 남고 같은 조합에만 못 들어감
UPDATE benefit SET conflict_group_code = 'G04' WHERE benefit_no IN (@fee1, @fee2);


-- ==================================================================
-- 3. 개별쌍 충돌 (benefit_conflict_rule)
-- ==================================================================
-- 룰북 규칙: 순서 없는 관계이므로 작은 번호를 trigger, 큰 번호를 target 으로 저장
-- schema v1.4 의 chk_conflict_rule_order CHECK 제약이 이 규칙을 강제한다.
--
-- v2.2: 모든 INSERT 를 VALUES 대신 SELECT ... WHERE 형태로 바꿨다.
--       변수가 NULL 이면 그 한 줄만 조용히 건너뛰고 다음 규칙으로 넘어간다.

DELETE FROM benefit_conflict_rule;

-- ------------------------------------------------------------------
-- 3-1. 확정·활성 — 엔진이 실제로 적용하는 규칙 (7건)
-- ------------------------------------------------------------------

-- (1) 보유 vs 후보 · 중복불가 → 후보에서 제거
--     근거: 둘 다 평택시 기획항만경제실의 청년창업 지원사업이며
--           동일 연도에 창업 사업화 지원을 중복으로 받는 형태가 된다.
INSERT INTO benefit_conflict_rule
(trigger_benefit_no, target_benefit_no, conflict_type, rule_text, confirm_status, is_active)
SELECT LEAST(@startup, @funding), GREATEST(@startup, @funding), '중복불가',
       '평택시 우수초기창업자 지원사업 선정자는 같은 해 청년창업 크라우드 펀딩 지원사업에 중복 신청할 수 없습니다.',
       '확정', 'Y'
WHERE @startup IS NOT NULL AND @funding IS NOT NULL;

-- (2) 보유 vs 후보 · 일부제한 → 후보에 남기고 경고 + 추천점수 20점 미부여
--     근거: 햇살론유스는 상환 중 부채로 잡히며, 청약통장 연계 대출 심사 시
--           우대 조건과 한도에 영향을 줄 수 있다.
INSERT INTO benefit_conflict_rule
(trigger_benefit_no, target_benefit_no, conflict_type, rule_text, confirm_status, is_active)
SELECT LEAST(@sunshine, @dream), GREATEST(@sunshine, @dream), '일부제한',
       '햇살론유스 상환 중에는 청년주택드림청약통장 연계 대출의 우대 조건이 일부 제한될 수 있습니다.',
       '확정', 'Y'
WHERE @sunshine IS NOT NULL AND @dream IS NOT NULL;

-- (3) 보유 vs 후보 · 확인필요 → 후보에서 제거하지는 않지만 경고가 붙고
--     내부 경고이므로 추천점수 20점을 받지 못한다.
--     근거: 두 사업 모두 청년 채무 부담 완화를 목적으로 하며,
--           채무조정 진행 상태에 따라 동시 이용 가능 여부가 달라진다.
INSERT INTO benefit_conflict_rule
(trigger_benefit_no, target_benefit_no, conflict_type, rule_text, confirm_status, is_active)
SELECT LEAST(@sunshine, @debt), GREATEST(@sunshine, @debt), '확인필요',
       '햇살론유스 상환 중 학자금대출 장기연체자 지원을 함께 신청하려면 채무조정 진행 상태를 먼저 확인해야 합니다.',
       '확정', 'Y'
WHERE @sunshine IS NOT NULL AND @debt IS NOT NULL;

-- (4) 조합 내부 · 중복불가 → 두 정책 모두 후보에는 남고 같은 조합에만 못 들어감
--     근거: 청년미래플러스(고용노동부)는 취업 컨설팅·맞춤형 직무교육·멘토링을,
--           안성시 청년내일캠프는 취업역량 향상 교육·자기소개서·면접 컨설팅을 제공한다.
--           지원 항목이 실질적으로 겹친다.
INSERT INTO benefit_conflict_rule
(trigger_benefit_no, target_benefit_no, conflict_type, rule_text, confirm_status, is_active)
SELECT LEAST(@future, @camp), GREATEST(@future, @camp), '중복불가',
       '청년미래플러스 구직청년 과정 참여 중에는 동일한 취업 컨설팅을 제공하는 안성시 청년내일캠프에 중복 참여할 수 없습니다.',
       '확정', 'Y'
WHERE @future IS NOT NULL AND @camp IS NOT NULL;

-- (5) 조합 내부 · 일부제한 → 조합에 함께 들어갈 수 있으나 경고 표시
--     근거: 청년키움지원센터와 청춘어람 모두 의성군이 운영하는 청년 활동 공간으로
--           프로그램 일부가 중복 편성된다.
INSERT INTO benefit_conflict_rule
(trigger_benefit_no, target_benefit_no, conflict_type, rule_text, confirm_status, is_active)
SELECT LEAST(@kium, @chung), GREATEST(@kium, @chung), '일부제한',
       '청년키움지원센터와 청춘어람은 일부 프로그램이 중복 운영되어 동일 프로그램은 한 곳에서만 참여할 수 있습니다.',
       '확정', 'Y'
WHERE @kium IS NOT NULL AND @chung IS NOT NULL;

-- (6)(7) 외부 제도 경고 (trigger 가 NULL) → 안내만, 제거도 점수 영향도 없다.
--     외부 제도는 청년타파가 추천하지 않는 제도라 비교 대상이 목록에 없다.
--     사용자가 실제로 그 제도를 받는지 알 수 없으므로 순위에 반영하지 않는다.
INSERT INTO benefit_conflict_rule
(trigger_benefit_no, target_benefit_no, conflict_type, rule_text, confirm_status, is_active)
SELECT NULL, @future, '확인필요',
       '실업급여 수급 중에는 청년미래플러스 참여가 제한될 수 있습니다. 관할 고용센터에 확인하세요.',
       '확정', 'Y'
WHERE @future IS NOT NULL;

INSERT INTO benefit_conflict_rule
(trigger_benefit_no, target_benefit_no, conflict_type, rule_text, confirm_status, is_active)
SELECT NULL, @camp, '확인필요',
       '국민취업지원제도 구직활동지원금을 받는 중이라면 안성시 청년내일캠프 참여 시 지원금 지급 요건을 먼저 확인하세요.',
       '확정', 'Y'
WHERE @camp IS NOT NULL;

-- ------------------------------------------------------------------
-- 3-2. 검수필요 — 관리자 검수 대기 상태. 엔진이 적용하지 않아야 한다. (3건)
--      근거는 있으나 각 기관 공고문으로 확인하지 못한 규칙을 여기에 둔다.
--      실제 운영에서 규칙이 쌓이는 과정을 그대로 재현한 것이다.
-- ------------------------------------------------------------------

INSERT INTO benefit_conflict_rule
(trigger_benefit_no, target_benefit_no, conflict_type, rule_text, confirm_status, is_active)
SELECT LEAST(@match, @ai), GREATEST(@match, @ai), '중복불가',
       '동일 기간에 두 개 이상의 직업훈련 과정을 중복 수강할 수 없습니다.',
       '검수필요', 'Y'
WHERE @match IS NOT NULL AND @ai IS NOT NULL;

INSERT INTO benefit_conflict_rule
(trigger_benefit_no, target_benefit_no, conflict_type, rule_text, confirm_status, is_active)
SELECT LEAST(@allow, @suit), GREATEST(@allow, @suit), '일부제한',
       '같은 면접 건에 대해 면접수당과 면접정장 대여를 함께 신청할 수 없습니다.',
       '검수필요', 'Y'
WHERE @allow IS NOT NULL AND @suit IS NOT NULL;

INSERT INTO benefit_conflict_rule
(trigger_benefit_no, target_benefit_no, conflict_type, rule_text, confirm_status, is_active)
SELECT LEAST(@incu, @kium), GREATEST(@incu, @kium), '중복불가',
       '의성군 청년 공간은 1인 1개소만 이용할 수 있습니다.',
       '검수필요', 'Y'
WHERE @incu IS NOT NULL AND @kium IS NOT NULL;

-- ------------------------------------------------------------------
-- 3-3. 비활성 — 폐기된 규칙. 엔진이 적용하지 않아야 한다. (1건)
-- ------------------------------------------------------------------

INSERT INTO benefit_conflict_rule
(trigger_benefit_no, target_benefit_no, conflict_type, rule_text, confirm_status, is_active)
SELECT LEAST(@camp, @portal), GREATEST(@camp, @portal), '중복불가',
       '2025년까지 적용되던 규칙으로 현재는 폐기되었습니다.',
       '확정', 'N'
WHERE @camp IS NOT NULL AND @portal IS NOT NULL;


-- ==================================================================
-- 4. 적재 확인
-- ==================================================================
-- 아래 세 줄의 결과가 주석의 기대값과 다르면, 위 진단 SELECT 에서
-- NULL 이었던 정책 때문에 그 정책이 걸린 규칙이 빠진 것이다.

SELECT COUNT(*) AS applied_cnt FROM applied_benefit WHERE member_no = 2;            -- 2

SELECT conflict_group_code, COUNT(*) AS cnt FROM benefit
WHERE conflict_group_code IS NOT NULL GROUP BY conflict_group_code;                -- G01 2 / G02 3 / G03 2 / G04 2

SELECT confirm_status, is_active, COUNT(*) AS cnt FROM benefit_conflict_rule
GROUP BY confirm_status, is_active;                                                -- 확정·Y 7 / 검수필요·Y 3 / 확정·N 1