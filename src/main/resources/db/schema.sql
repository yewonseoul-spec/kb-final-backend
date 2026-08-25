-- =====================================================================
-- [v2.8] AI 관측 원본 보존층
--        benefit_conflict_analysis_run 테이블 신설 (AI 를 부른 사실 자체를 남긴다)
--        benefit_conflict_observation 테이블 신설 (AI 가 말한 관계를 정리 이전 원본으로 남긴다)
--        benefit_conflict_candidate 에 canonical_run_no / reconciliation_status /
--        observation_count 컬럼 추가 (정리 결과를 관측과 이어붙인다)
--        (같은 정책을 다시 분석해 결과가 달라졌을 때
--         AI 가 다르게 말한 것인지 정리 규칙이 다르게 접은 것인지 구분하기 위해서다)
--        ★ 팀 대화에서는 이 변경을 「v2.7」이라고 불렀다.
--          아래 [v2.7] 이 이미 다른 변경을 가리키고 있어 번호를 올렸다.
--          증분 스크립트 : db/migration/v2.8_conflict_observation.sql
-- =====================================================================
-- [v2.7] AI 중복수혜 자동분류 + 프롬프트 관리
--        ai_prompt 테이블 신설 (AI 지시문을 코드가 아니라 DB에서 버전 관리)
--        benefit_conflict_candidate 테이블 신설 (AI 분석 결과 보존층)
--        (Candidate 는 공고문 의미를 그대로 보존하고,
--         benefit_conflict_rule 은 엔진이 실행할 수 있는 subset 만 담는다)
-- =====================================================================
-- [v2.6] member_profile household_size 컬럼·CHECK 제거
-- =====================================================================
-- [v2.5] notification 테이블 noti_type 에 'SECURITY' 추가, ref_no 컬럼 추가
--        (보안 알림을 계정 알림과 분리. 수신 거부 대상이 아니기 때문)
--        (ref_no = 알림이 가리키는 대상 번호. 마감 알림이면 benefit_no)
-- =====================================================================
-- =====================================================================
-- [v2.4] benefit 테이블에 admin_is_active, api_is_active 컬럼 추가
--        sync_log에 delete_cnt 추가, sync_log_detail action_type에 'D' 추가
--        trg_benefit_keep_admin_active 트리거 신설 (파일 맨 아래)
--        (관리자가 지정한 활성 상태를 동기화가 덮지 못하게 하고,
--         API가 같은 값으로 정상화되면 지정을 자동 해제한다)
-- =====================================================================
-- [v2.3] 4-3 goal_benefit_category 매핑테이블 추가
--        (목표 → 혜택 중분류. priority 1=핵심, 2=연관)
-- =====================================================================
-- [v2.2] 5-2. spending_benefit_category_map 테이블 추가
-- =====================================================================
-- [v2.1] benefit 테이블 api_deleted_yn, api_deleted_dt 컬럼 추가
-- =====================================================================
-- [v2.0] benefit 테이블 ref_url_addr1 컬럼 추가
-- =====================================================================
-- [v1.9] benefit 테이블에 custom_apply_url 컬럼 추가
--        (관리자가 지정한 신청 링크. 동기화 upsert 대상에서 제외해 보존한다)
-- =====================================================================
-- =====================================================================
-- [v1.8] spending, expected_spending 테이블에서 account_id 컬럼 삭제, expected_spending 테이블에 auto_generated 컬럼 추가
-- =====================================================================
-- =====================================================================
-- [v1.7] benefit 테이블에 mclsf_nm 컬럼 추가, 4-2 benefit_detail_category 매핑테이블 추가
-- =====================================================================

-- =====================================================================
-- [v1.6] sync_log에 sync_start_date, sync_end_date 추가 (관리자 기간별 동기화 대상 기간)
--        sync_log_detail 테이블 신설 (동기화가 어떤 혜택을 어떻게 처리했는지 기록)
-- =====================================================================
-- =====================================================================
-- [v1.5] goal 테이블에 member_no unique 제약 추가
-- =====================================================================
-- =====================================================================
-- [v1.4] terms 테이블에 title, terms_type 칼럼 추가
-- =====================================================================
-- =====================================================================
--  청년타파 (Youth-Tapa) - schema.sql
--  DBMS      : MySQL 8.0+ (InnoDB / utf8mb4)
--  컨벤션    : snake_case·단수형, 무접두사 / 제약 fk_·uk_·idx_ / ENUM 대문자
--  공통      : PK = PRIMARY KEY(고정) · created_at/updated_at/status(is_active)
--  참고      : 조건부 업무규칙(관리자 ROLE 검증, 시나리오별 필수값 등)은
--              서비스 계층에서 처리. 여기서는 정적·단순 규칙만 CHECK로 강제.
--  Soft Delete 설계이므로 FK는 CASCADE 없이 기본(RESTRICT) 유지.
--  ---------------------------------------------------------------------
--  [최신 반영 요약] 총 26개 테이블 (기존 22 + common_code + benefit_major/school/job)
--   · API 출처 코드(코드정보 탭 11개군 69건)를 ENUM -> common_code 통합
--     테이블 + FK로 전환. 코드군 검증은 CHECK (컬럼 LIKE '00NN%') 로 보완
--   · benefit : created_at->first_reg_dt, updated_at->last_mdfcn_dt(API 필드명),
--               earn_cnd_se_cd·earn_etc_cn 추가, 전공/학력/취업 다중값 컬럼 제거
--   · benefit_major / benefit_school / benefit_job 신규 : 다중값 정규화
--   · benefit_category : API 대분류(lclsfNm) 5종과 1:1, lclsf_nm 매칭키 추가
--     (대분류는 7자리 코드가 없어 common_code 에 포함하지 않고 별도 유지)
--   · goal_type/noti_type/role 등 자체 정의 고정값은 ENUM 유지(외부 코드 아님)
-- =====================================================================

CREATE DATABASE IF NOT EXISTS youthtapa
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE youthtapa;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
--  초기화 (재실행 대비) — 자식 → 부모 역순 DROP
-- ---------------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS benefit_conflict_candidate;
-- (v2.8) 아래 둘은 FK 때문에 순서가 있다.
--        observation 과 candidate 가 analysis_run 을 참조하므로
--        analysis_run 을 마지막에 지운다.
DROP TABLE IF EXISTS benefit_conflict_observation;
DROP TABLE IF EXISTS benefit_conflict_analysis_run;
DROP TABLE IF EXISTS ai_prompt;
DROP TABLE IF EXISTS sync_log_detail;
DROP TABLE IF EXISTS sync_log;
DROP TABLE IF EXISTS benefit_conflict_rule;
DROP TABLE IF EXISTS applied_benefit;
DROP TABLE IF EXISTS benefit_job;
DROP TABLE IF EXISTS benefit_school;
DROP TABLE IF EXISTS benefit_major;
DROP TABLE IF EXISTS benefit_region;
DROP TABLE IF EXISTS favorite_benefit;
DROP TABLE IF EXISTS expected_spending;
DROP TABLE IF EXISTS spending;
DROP TABLE IF EXISTS member_finance_product;
DROP TABLE IF EXISTS account;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS goal;
DROP TABLE IF EXISTS member_terms_agree;
DROP TABLE IF EXISTS member_profile;
DROP TABLE IF EXISTS benefit;
DROP TABLE IF EXISTS stress_scenario;
DROP TABLE IF EXISTS recommend_keyword;
DROP TABLE IF EXISTS finance_product;
DROP TABLE IF EXISTS goal_benefit_category;
DROP TABLE IF EXISTS spending_benefit_category_map;
DROP TABLE IF EXISTS spending_category;
DROP TABLE IF EXISTS benefit_category;
DROP TABLE IF EXISTS benefit_detail_category;
DROP TABLE IF EXISTS region;
DROP TABLE IF EXISTS terms;
DROP TABLE IF EXISTS member;
-- 코드 테이블 (FK 부모 : 가장 마지막에 DROP)
DROP TABLE IF EXISTS common_code;
SET FOREIGN_KEY_CHECKS = 1;


-- =====================================================================
--  [코드 테이블] 온통청년 API 코드 정보 (API코드정보.xlsx > 코드정보 탭)
--   · 값은 data_0_code.sql 로 적재 : 11개 코드군 69건
--     (0011 전공 / 0013 취업 / 0014 특화 / 0042 제공방법 / 0043 소득조건 /
--      0044 승인상태 / 0049 학력 / 0054 제공기관그룹 / 0055 결혼 /
--      0056 사업기간 / 0057 신청기간)
--   · 통합 1테이블 구조. 온통청년 코드는 앞 4자리에 코드군이 포함되어
--     7자리 코드 자체가 전역 유일하므로 code 단독 PK로 FK 참조가 가능하다.
--   · 단, FK는 "코드의 존재"만 검증하고 "올바른 코드군"인지는 보지 못하므로
--     (예: employ_status 에 학력코드 0049001 이 들어가도 FK 통과)
--     참조하는 컬럼마다 CHECK (컬럼 LIKE '00NN%') 로 코드군을 함께 강제한다.
--   · 최신 설계서에 따라 display_order와 is_active를 두어
--     필터 노출 순서와 코드 사용 여부를 관리한다.
-- =====================================================================
CREATE TABLE common_code
(
    code          CHAR(7)     NOT NULL COMMENT '코드(전역 유일, 예: 0013004)',
    group_code    CHAR(4)     NOT NULL COMMENT '코드군(예: 0013)',
    api_field     VARCHAR(30) NOT NULL COMMENT 'API 필드명(예: jobCd) - 동기화 매핑용',
    group_name    VARCHAR(50) NOT NULL COMMENT '코드군명(예: 정책취업요건코드)',
    code_name     VARCHAR(50) NOT NULL COMMENT '코드명(예: 재직자)',
    display_order INT         NULL COMMENT '필터 화면 노출 순서',
    is_active     CHAR(1)     NOT NULL DEFAULT 'Y' COMMENT '사용여부 Y/N',
    PRIMARY KEY (code),
    CONSTRAINT chk_common_code_group CHECK (code LIKE CONCAT(group_code, '%')),
    CONSTRAINT chk_common_code_active CHECK (is_active IN ('Y', 'N')),
    INDEX idx_common_code_group (group_code),
    INDEX idx_common_code_api_field (api_field)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='공통코드(온통청년 API 코드정보)';


-- =====================================================================
--  1. member : 회원
-- =====================================================================
CREATE TABLE member
(
    member_no  INT                   NOT NULL AUTO_INCREMENT COMMENT '회원번호',
    login_id   VARCHAR(30)           NOT NULL COMMENT '아이디',
    password   VARCHAR(255)          NOT NULL COMMENT '비밀번호(BCrypt 해시)',
    email      VARCHAR(100)          NOT NULL COMMENT '이메일',
    role       ENUM ('USER','ADMIN') NOT NULL DEFAULT 'USER' COMMENT '권한',
    real_name  VARCHAR(20)           NOT NULL COMMENT '실명(본인확인용)',
    status     CHAR(1)               NOT NULL DEFAULT 'Y' COMMENT '회원상태 Y/N(탈퇴 시 N, Soft Delete)',
    created_at DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입일시',
    updated_at DATETIME              NULL     DEFAULT NULL COMMENT '수정일시(MyBatis UPDATE로 갱신)',
    PRIMARY KEY (member_no),
    CONSTRAINT uk_member_login_id UNIQUE (login_id),
    CONSTRAINT uk_member_email UNIQUE (email),
    CONSTRAINT chk_member_status CHECK (status IN ('Y', 'N'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='회원';


-- =====================================================================
--  2. terms : 약관
-- =====================================================================
CREATE TABLE terms
(
    terms_no    INT                   NOT NULL AUTO_INCREMENT COMMENT '약관번호',
    title       VARCHAR(100)          NOT NULL COMMENT '약관제목',
    content     TEXT                  NOT NULL COMMENT '약관내용',
    is_required CHAR(1)               NOT NULL COMMENT '필수여부 Y/N',
    terms_type  ENUM ('SIGNUP', 'AI') NOT NULL COMMENT '용도 구분',
    version     CHAR(3)               NOT NULL COMMENT '버전(예: v10)',
    PRIMARY KEY (terms_no),
    CONSTRAINT chk_terms_is_required CHECK (is_required IN ('Y', 'N'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='약관';


-- =====================================================================
--  3. region : 지역 (법정시군구코드, 자기참조)
-- =====================================================================
CREATE TABLE region
(
    zip_cd             CHAR(5)     NOT NULL COMMENT '지역코드(법정시군구코드)',
    region_name        VARCHAR(50) NOT NULL COMMENT '지역명',
    parent_region_code CHAR(5)     NULL COMMENT '상위지역코드(시/도·전국은 NULL)',
    PRIMARY KEY (zip_cd),
    CONSTRAINT fk_region_parent FOREIGN KEY (parent_region_code)
        REFERENCES region (zip_cd)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='지역';


-- =====================================================================
--  4. benefit_category : 혜택카테고리
-- =====================================================================
--  · API 대분류(lclsfNm) 5종과 1:1. 폴백(99 미분류) 미사용 →
--    미정의 대분류 수신 시 동기화 로직에서 예외 처리 필요.
--  · API 응답에는 대분류 '코드'가 없고 이름 문자열만 오므로 lclsf_nm 을
--    매칭 키로 사용. 파일 표기("교육")와 실제 응답("교육･직업훈련")이
--    다르고 가운뎃점도 반각(U+FF65)이라, 반드시 응답 원문을 저장할 것.
CREATE TABLE benefit_category
(
    category_code CHAR(2)     NOT NULL COMMENT '카테고리코드(01~05)',
    category_name VARCHAR(30) NOT NULL COMMENT '카테고리명(화면 표시용)',
    lclsf_nm      VARCHAR(50) NOT NULL COMMENT '정책대분류명(lclsfNm, API 원문·동기화 매칭키)',
    display_order INT         NULL COMMENT '표시순서',
    PRIMARY KEY (category_code),
    CONSTRAINT uk_benefit_category_lclsf_nm UNIQUE (lclsf_nm)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='혜택카테고리(API 대분류 1:1)';

-- =====================================================================
--  4-2  benefit_detail_categorg : 혜택 중분류 카테고리
-- =====================================================================
CREATE TABLE benefit_detail_category
(
    detail_category_code CHAR(2)     NOT NULL COMMENT '중분류카테고리코드',
    detail_category_name VARCHAR(50) NOT NULL COMMENT '중분류카테고리명',
    mclsf_nm             VARCHAR(50) NOT NULL COMMENT '정책대분류명',
    display_order        INT         NOT NULL COMMENT '표시순서',
    PRIMARY KEY (detail_category_code),
    CONSTRAINT uk_benefit_detail_category_mclsf_nm UNIQUE (mclsf_nm)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='혜택 중분류 카테고리';

-- =====================================================================
--  4-3  goal_benefit_category : 목표 → 혜택 중분류 매핑
--   · 목표 기반 추천이 후보를 좁히는 데 쓴다.
--   · priority 1 = 핵심(목표에 직접 맞닿음), 2 = 연관(함께 보면 좋음)
--     화면에서는 두 섹션으로 나누어 보여 준다.
--   · PK가 (goal_type, detail_category_code) 라 같은 목표 안에서
--     한 중분류가 1차와 2차에 동시에 들어갈 수 없다. 섹션 간 중복 방지.
--   · goal_type 은 goal 테이블과 같은 ENUM 이다. 목표를 추가하면
--     goal · goal_benefit_category · GoalType.java 세 곳을 함께 고쳐야 한다.
-- =====================================================================
CREATE TABLE goal_benefit_category
(
    goal_type            ENUM ('INDEPENDENCE','EMPLOYMENT','STARTUP','MARRIAGE','STUDY_ABROAD') NOT
                                                                                                    NULL COMMENT '목표유형',
    detail_category_code CHAR(2) NOT NULL COMMENT '중분류카테고리코드',
    priority             TINYINT NOT NULL COMMENT '우선순위(1=핵심, 2=연관)',
    PRIMARY KEY (goal_type, detail_category_code),
    CONSTRAINT fk_goal_benefit_category_detail FOREIGN KEY (detail_category_code)
        REFERENCES benefit_detail_category (detail_category_code),
    CONSTRAINT chk_goal_benefit_category_priority CHECK (priority IN (1, 2))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='목표별 혜택 중분류 매핑';

-- =====================================================================
--  5. spending_category : 소비 카테고리
-- =====================================================================
CREATE TABLE spending_category
(
    category_no   INT         NOT NULL AUTO_INCREMENT COMMENT '카테고리번호',
    category_name VARCHAR(30) NOT NULL COMMENT '카테고리명',
    PRIMARY KEY (category_no),
    CONSTRAINT uk_spending_category_name UNIQUE (category_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='소비 카테고리';

-- 5-2. 소비 카테고리-혜택 중분류 매핑 (spending_benefit_category_map) :
CREATE TABLE spending_benefit_category_map
(
    category_no          INT     NOT NULL COMMENT '소비 카테고리 번호',
    detail_category_code CHAR(2) NOT NULL COMMENT '혜택 중분류 코드',

    PRIMARY KEY (
                 category_no,
                 detail_category_code
        ),

    CONSTRAINT fk_spending_benefit_map_spending
        FOREIGN KEY (category_no)
            REFERENCES spending_category(category_no),

    CONSTRAINT fk_spending_benefit_map_benefit
        FOREIGN KEY (detail_category_code)
            REFERENCES benefit_detail_category(detail_category_code)
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '소비 카테고리-혜택 중분류 매핑';


-- =====================================================================
--  6. finance_product : 금융 상품
-- =====================================================================
CREATE TABLE finance_product
(
    product_no    INT                                                             NOT NULL AUTO_INCREMENT COMMENT '상품번호',
    product_name  VARCHAR(100)                                                    NOT NULL COMMENT '상품명',
    product_type  ENUM ('DEPOSIT','SAVINGS','SUBSCRIPTION','INSURANCE','PENSION') NOT NULL
        COMMENT '상품유형(예금/적금/청약/보험·공제/퇴직연금)',
    org_name      VARCHAR(100)                                                    NOT NULL COMMENT '금융기관명',
    interest_rate DECIMAL(5, 2)                                                   NULL COMMENT '연이율',
    PRIMARY KEY (product_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='금융 상품';


-- =====================================================================
--  7. recommend_keyword : 추천검색어
-- =====================================================================
CREATE TABLE recommend_keyword
(
    keyword_code  INT         NOT NULL AUTO_INCREMENT COMMENT '키워드코드',
    keyword_name  VARCHAR(50) NOT NULL COMMENT '키워드명',
    display_order INT         NULL COMMENT '표시순서',
    is_active     CHAR(1)     NOT NULL DEFAULT 'Y' COMMENT '활성화여부 Y/N',
    PRIMARY KEY (keyword_code),
    CONSTRAINT chk_recommend_keyword_active CHECK (is_active IN ('Y', 'N'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='추천검색어';


-- =====================================================================
--  8. stress_scenario : 스트레스 테스트 시나리오
--     복합 UNIQUE(scenario_code, shock_level)
-- =====================================================================
CREATE TABLE stress_scenario
(
    scenario_no     INT                       NOT NULL AUTO_INCREMENT COMMENT '시나리오번호',
    scenario_code   VARCHAR(20)               NOT NULL COMMENT '시나리오코드(INFLATION/MEDICAL/RENT/RATE/COMPLEX)',
    scenario_name   VARCHAR(50)               NOT NULL COMMENT '시나리오명',
    description     VARCHAR(200)              NULL COMMENT '설명',
    shock_level     ENUM ('LOW','MID','HIGH') NOT NULL COMMENT '충격강도',
    target_category VARCHAR(100)              NOT NULL COMMENT '영향카테고리(COMPLEX는 ALL)',
    change_rate     DECIMAL(4, 3)             NULL COMMENT '변동률(0~1, 0.100=10%)',
    fixed_amount    BIGINT                    NULL COMMENT '고정추가금액(원)',
    PRIMARY KEY (scenario_no),
    CONSTRAINT uk_stress_scenario_code_level UNIQUE (scenario_code, shock_level),
    CONSTRAINT chk_stress_change_rate CHECK (change_rate IS NULL OR (change_rate >= 0 AND change_rate <= 1)),
    CONSTRAINT chk_stress_fixed_amount CHECK (fixed_amount IS NULL OR fixed_amount >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='스트레스 테스트 시나리오';


-- =====================================================================
--  9. benefit : 청년지원혜택 (온통청년 API 매핑)
-- =====================================================================
CREATE TABLE benefit
(
    benefit_no           INT          NOT NULL AUTO_INCREMENT COMMENT '혜택번호(내부 PK)',
    plcy_no              VARCHAR(30)  NULL COMMENT '외부정책ID(plcyNo, 동기화 매칭키)',
    plcy_nm              TEXT         NOT NULL COMMENT '혜택명(plcyNm)',
    category_code        CHAR(2)      NOT NULL COMMENT '카테고리코드',
    detail_category_code CHAR(2)      NULL COMMENT '중분류카테고리코드',
    sprvsn_inst_cd_nm    VARCHAR(100) NULL COMMENT '주관기관명(sprvsnInstCdNm)',
    target_desc          TEXT         NULL COMMENT '지원대상',
    plcy_sprt_cn         TEXT         NULL COMMENT '지원내용(plcySprtCn)',
    support_amount       INT          NULL COMMENT '지원금액(파싱)',
    plcy_aply_mthd_cn    TEXT         NULL COMMENT '신청방법(plcyAplyMthdCn)',
    sbmsn_dcmnt_cn       TEXT         NULL COMMENT '제출서류(sbmsnDcmntCn)',
    apply_start_date     DATE         NULL COMMENT '신청시작일',
    apply_end_date       DATE         NULL COMMENT '신청종료일(D-Day 기준)',
    aply_ymd             VARCHAR(200) NULL COMMENT '신청기간원문(aplyYmd)',
    aply_prd_se_cd       CHAR(7)      NULL COMMENT '신청기간구분코드(0057 계열)',
    aply_url_addr        TEXT         NULL COMMENT '신청URL(aplyUrlAddr)',
    ref_url_addr1        TEXT         NULL COMMENT '참고 URL 1',
    custom_apply_url     TEXT         NULL COMMENT '관리자 지정 신청URL(동기화로 덮이지 않음)',
    sprt_trgt_min_age    INT          NULL COMMENT '최소연령(sprtTrgtMinAge)',
    sprt_trgt_max_age    INT          NULL COMMENT '최대연령(sprtTrgtMaxAge)',
    earn_cnd_se_cd       CHAR(7)      NULL COMMENT '소득조건구분코드(earnCndSeCd) 0043',
    earn_min_amt         INT          NULL COMMENT '최소소득(earnMinAmt)',
    earn_max_amt         INT          NULL COMMENT '최대소득(earnMaxAmt)',
    earn_etc_cn          TEXT         NULL COMMENT '소득기타내용(earnEtcCn, 0043003일 때 조건 원문)',
    mrg_stts_cd          CHAR(7)      NULL COMMENT '결혼상태코드(mrgSttsCd) 0055',
    conflict_group_code  VARCHAR(50)  NULL COMMENT '중복수혜그룹코드',
    inq_cnt              INT          NOT NULL DEFAULT 0 COMMENT '조회수(초기값; 실시간은 Redis)',
    is_active            CHAR(1)      NOT NULL DEFAULT 'Y' COMMENT '활성화여부 Y/N(마감 경과 시 N)',
    admin_is_active      CHAR(1)      NULL COMMENT '관리자 지정 활성 상태. NULL이면 API 원본을 따른다',
    api_is_active        CHAR(1)      NULL COMMENT 'API 원본 활성 상태. 관리자 지정과 비교해 자동 해제를 판정한다',
    api_deleted_yn        CHAR(1)     NOT NULL DEFAULT 'N' COMMENT '혜택 삭제 여부',
    api_deleted_dt       DATETIME     NULL COMMENT '혜택 삭제 일시',
    frst_reg_dt          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초등록일시(frstRegDt)',
    last_mdfcn_dt        DATETIME     NULL     DEFAULT NULL COMMENT '최종수정일시(lastMdfcnDt)',
    plcy_expln_cn        TEXT         NULL COMMENT '정책설명내용(plcyExplnCn)',

    PRIMARY KEY (benefit_no),
    CONSTRAINT uk_benefit_plcy_no UNIQUE (plcy_no),
    CONSTRAINT fk_benefit_category FOREIGN KEY (category_code)
        REFERENCES benefit_category (category_code),
    CONSTRAINT fk_benefit_detail_category FOREIGN KEY (detail_category_code)
        REFERENCES benefit_detail_category (detail_category_code),
    CONSTRAINT fk_benefit_mrg FOREIGN KEY (mrg_stts_cd) REFERENCES common_code (code),
    CONSTRAINT fk_benefit_earn_cnd FOREIGN KEY (earn_cnd_se_cd) REFERENCES common_code (code),
    CONSTRAINT fk_benefit_aply_prd_se_cd FOREIGN KEY (aply_prd_se_cd) REFERENCES common_code (code),
    -- FK는 코드 존재만 검증하므로 코드군까지 CHECK로 강제 (NULL은 UNKNOWN이라 통과)
    -- 혜택 측은 '제한없음'이 정상 조건값이므로 별도 차단하지 않는다
    CONSTRAINT chk_benefit_mrg CHECK (mrg_stts_cd IS NULL OR mrg_stts_cd LIKE '0055%'),
    CONSTRAINT chk_benefit_earn_cnd CHECK (earn_cnd_se_cd IS NULL OR earn_cnd_se_cd LIKE '0043%'),
    CONSTRAINT chk_benefit_is_active CHECK (is_active IN ('Y', 'N')),
    CONSTRAINT chk_benefit_aply_prd_se_cd CHECK (aply_prd_se_cd IS NULL OR aply_prd_se_cd LIKE '0057%'),
    INDEX idx_benefit_apply_end (apply_end_date),
    INDEX idx_benefit_is_active (is_active)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='청년지원혜택';


-- =====================================================================
--  10. member_profile : 회원 프로필 (member와 1:1, member_no = PK+FK)
-- =====================================================================
CREATE TABLE member_profile
(
    member_no        INT          NOT NULL COMMENT '회원번호(PK이자 FK, 자동채번 아님)',
    birth_date       DATE         NULL COMMENT '생년월일(나이 자격 판정)',
    region_code      CHAR(5)      NULL COMMENT '지역코드',
    income           INT          NULL COMMENT '소득(실제값)',
    employ_status    CHAR(7)      NULL COMMENT '취업상태(jobCd 0013)',
    major            CHAR(7)      NULL COMMENT '전공(plcyMajorCd 0011)',
    education        CHAR(7)      NULL COMMENT '학력(schoolCd 0049)',
    mrg_stts_cd      CHAR(7)      NULL COMMENT '결혼상태(mrgSttsCd 0055)',
    profile_img_path VARCHAR(255) NULL COMMENT '프로필이미지경로(경로/URL만 저장)',
    updated_at       DATETIME     NULL DEFAULT NULL COMMENT '수정일시(MyBatis UPDATE로 갱신)',
    PRIMARY KEY (member_no),
    CONSTRAINT fk_member_profile_member FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT fk_member_profile_region FOREIGN KEY (region_code)
        REFERENCES region (zip_cd),
    CONSTRAINT fk_member_profile_job FOREIGN KEY (employ_status) REFERENCES common_code (code),
    CONSTRAINT fk_member_profile_major FOREIGN KEY (major) REFERENCES common_code (code),
    CONSTRAINT fk_member_profile_school FOREIGN KEY (education) REFERENCES common_code (code),
    CONSTRAINT fk_member_profile_mrg FOREIGN KEY (mrg_stts_cd) REFERENCES common_code (code),
    -- LIKE : 통합 코드테이블이라 FK가 코드군을 못 보므로 코드군을 강제
    -- <>   : '제한없음'은 혜택의 조건값일 뿐 사람의 상태가 될 수 없으므로 회원 측만 차단
    -- NULL은 CHECK 평가 결과가 UNKNOWN이라 통과 → 프로필 미입력 허용과 충돌하지 않음
    CONSTRAINT chk_member_profile_job CHECK (employ_status LIKE '0013%' AND employ_status <> '0013010'),
    CONSTRAINT chk_member_profile_major CHECK (major LIKE '0011%' AND major <> '0011009'),
    CONSTRAINT chk_member_profile_school CHECK (education LIKE '0049%' AND education <> '0049010'),
    CONSTRAINT chk_member_profile_mrg CHECK (mrg_stts_cd LIKE '0055%' AND mrg_stts_cd <> '0055003')
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='회원 프로필';


-- =====================================================================
--  11. member_terms_agree : 회원 약관동의
-- =====================================================================
CREATE TABLE member_terms_agree
(
    agree_no  INT      NOT NULL AUTO_INCREMENT COMMENT '동의번호',
    member_no INT      NOT NULL COMMENT '회원번호',
    terms_no  INT      NOT NULL COMMENT '약관번호',
    is_agreed CHAR(1)  NOT NULL COMMENT '동의여부 Y/N',
    agreed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '동의일시',
    PRIMARY KEY (agree_no),
    CONSTRAINT uk_member_terms_agree UNIQUE (member_no, terms_no),
    CONSTRAINT fk_member_terms_agree_member FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT fk_member_terms_agree_terms FOREIGN KEY (terms_no)
        REFERENCES terms (terms_no),
    CONSTRAINT chk_member_terms_agree CHECK (is_agreed IN ('Y', 'N'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='회원 약관동의';


-- =====================================================================
--  12. goal : 목표
-- =====================================================================
CREATE TABLE goal
(
    goal_no    INT                                                                    NOT NULL AUTO_INCREMENT COMMENT '목표번호',
    member_no  INT                                                                    NOT NULL COMMENT '회원번호',
    goal_type  ENUM ('INDEPENDENCE','EMPLOYMENT','STARTUP','MARRIAGE','STUDY_ABROAD') NOT NULL
        COMMENT '목표유형(독립/취업/창업/결혼/유학)',
    created_at DATETIME                                                               NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '설정일시',
    PRIMARY KEY (goal_no),
    CONSTRAINT uk_goal_member UNIQUE (member_no),
    CONSTRAINT fk_goal_member FOREIGN KEY (member_no)
        REFERENCES member (member_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='목표';


-- =====================================================================
--  13. notification : 알림
-- =====================================================================
CREATE TABLE notification
(
    noti_no    INT NOT NULL AUTO_INCREMENT COMMENT '알림번호',
    member_no  INT NOT NULL COMMENT '회원번호',
    noti_type  ENUM ('DEADLINE','SPENDING','NEW_BENEFIT','ACCOUNT','SECURITY') NOT NULL COMMENT '알림유형(마감임박/소비분석/신규혜택/계정·정보수정/보안)',
    ref_no     INT NULL COMMENT '알림이 가리키는 대상 번호(마감 알림=benefit_no)',
    content    TEXT NOT NULL COMMENT '내용',
    is_read    CHAR(1) NOT NULL DEFAULT 'N' COMMENT '읽음여부 Y/N',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    PRIMARY KEY (noti_no),
    CONSTRAINT fk_notification_member FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT chk_notification_is_read CHECK (is_read IN ('Y', 'N'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='알림';

-- =====================================================================
--  14. account : 계좌
-- =====================================================================
CREATE TABLE account
(
    account_id INT         NOT NULL AUTO_INCREMENT COMMENT '식별번호',
    member_no  INT         NOT NULL COMMENT '회원번호',
    bank_name  VARCHAR(50) NOT NULL COMMENT '은행명',
    account_no VARCHAR(30) NOT NULL COMMENT '계좌번호',
    balance    BIGINT      NOT NULL DEFAULT 0 COMMENT '잔액',
    PRIMARY KEY (account_id),
    CONSTRAINT uk_account_no UNIQUE (account_no),
    CONSTRAINT fk_account_member FOREIGN KEY (member_no)
        REFERENCES member (member_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='계좌';


-- =====================================================================
--  15. member_finance_product : 사용자 금융 상품
-- =====================================================================
CREATE TABLE member_finance_product
(
    link_no       INT    NOT NULL AUTO_INCREMENT COMMENT '연결번호',
    member_no     INT    NOT NULL COMMENT '회원번호',
    product_no    INT    NOT NULL COMMENT '상품번호',
    hold_amount   BIGINT NOT NULL DEFAULT 0 COMMENT '보유금액',
    join_date     DATE   NULL COMMENT '가입일',
    maturity_date DATE   NULL COMMENT '만기일',
    PRIMARY KEY (link_no),
    CONSTRAINT fk_member_finance_product_member FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT fk_member_finance_product_product FOREIGN KEY (product_no)
        REFERENCES finance_product (product_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='사용자 금융 상품';


-- =====================================================================
--  16. spending : 소비 내역
-- =====================================================================
CREATE TABLE spending
(
    spending_no   INT                                                            NOT NULL AUTO_INCREMENT COMMENT '소비번호',
    member_no     INT                                                            NOT NULL COMMENT '회원번호',
    category_no   INT                                                            NOT NULL COMMENT '카테고리번호',
    spending_date DATE                                                           NOT NULL COMMENT '소비일자',
    amount        BIGINT                                                         NOT NULL COMMENT '소비금액',
    merchant      VARCHAR(100)                                                   NULL COMMENT '사용처',
    pay_method    ENUM ('CASH','CHECK_CARD','CREDIT_CARD','EASY_PAY','TRANSFER') NOT NULL
        COMMENT '결제수단(현금/체크카드/신용카드/간편결제/계좌이체)',
    memo          VARCHAR(200)                                                   NULL COMMENT '메모',
    PRIMARY KEY (spending_no),
    CONSTRAINT fk_spending_member FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT fk_spending_category FOREIGN KEY (category_no)
        REFERENCES spending_category (category_no),
    INDEX idx_spending_member_date (member_no, spending_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='소비 내역';


-- =====================================================================
--  17. expected_spending : 예상 소비
-- =====================================================================
CREATE TABLE expected_spending
(
    expected_no     INT          NOT NULL AUTO_INCREMENT COMMENT '예상소비번호',
    member_no       INT          NOT NULL COMMENT '회원번호',
    category_no     INT          NOT NULL COMMENT '카테고리번호',
    expected_date   DATE         NOT NULL COMMENT '소비예정일',
    expected_amount BIGINT       NOT NULL COMMENT '예상금액',
    merchant        VARCHAR(100) NULL COMMENT '사용처',
    memo            VARCHAR(200) NULL COMMENT '메모',
    auto_generated  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '자동 등록 여부',
    PRIMARY KEY (expected_no),
    CONSTRAINT fk_expected_spending_member FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT fk_expected_spending_category FOREIGN KEY (category_no)
        REFERENCES spending_category (category_no),
    INDEX idx_expected_spending_member_date (member_no, expected_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='예상 소비';


-- =====================================================================
--  18. favorite_benefit : 관심혜택  (UNIQUE(member_no, benefit_no))
-- =====================================================================
CREATE TABLE favorite_benefit
(
    favorite_no INT      NOT NULL AUTO_INCREMENT COMMENT '관심혜택번호',
    member_no   INT      NOT NULL COMMENT '회원번호',
    benefit_no  INT      NOT NULL COMMENT '혜택번호',
    saved_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '저장일시',
    PRIMARY KEY (favorite_no),
    CONSTRAINT uk_favorite_benefit_member_benefit UNIQUE (member_no, benefit_no),
    CONSTRAINT fk_favorite_benefit_member FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT fk_favorite_benefit_benefit FOREIGN KEY (benefit_no)
        REFERENCES benefit (benefit_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='관심혜택';


-- =====================================================================
--  19. benefit_region : 혜택지역 매핑 (복합 PK)
-- =====================================================================
CREATE TABLE benefit_region
(
    benefit_no INT     NOT NULL COMMENT '혜택번호',
    zip_cd     CHAR(5) NOT NULL COMMENT '지역코드',
    PRIMARY KEY (benefit_no, zip_cd),
    CONSTRAINT fk_benefit_region_benefit FOREIGN KEY (benefit_no)
        REFERENCES benefit (benefit_no),
    CONSTRAINT fk_benefit_region_region FOREIGN KEY (zip_cd)
        REFERENCES region (zip_cd)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='혜택지역 매핑';


-- =====================================================================
--  19-2. benefit_major : 혜택 전공요건 매핑 (복합 PK)
-- =====================================================================
CREATE TABLE benefit_major
(
    benefit_no    INT     NOT NULL COMMENT '혜택번호',
    plcy_major_cd CHAR(7) NOT NULL COMMENT '전공요건코드(0011 계열)',
    PRIMARY KEY (benefit_no, plcy_major_cd),
    CONSTRAINT fk_benefit_major_benefit FOREIGN KEY (benefit_no)
        REFERENCES benefit (benefit_no),
    CONSTRAINT fk_benefit_major_code FOREIGN KEY (plcy_major_cd)
        REFERENCES common_code (code),
    CONSTRAINT chk_benefit_major_group CHECK (plcy_major_cd LIKE '0011%')
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='혜택 전공요건 매핑';


-- =====================================================================
--  19-3. benefit_school : 혜택 학력요건 매핑 (복합 PK)
-- =====================================================================
CREATE TABLE benefit_school
(
    benefit_no INT     NOT NULL COMMENT '혜택번호',
    school_cd  CHAR(7) NOT NULL COMMENT '학력요건코드(0049 계열)',
    PRIMARY KEY (benefit_no, school_cd),
    CONSTRAINT fk_benefit_school_benefit FOREIGN KEY (benefit_no)
        REFERENCES benefit (benefit_no),
    CONSTRAINT fk_benefit_school_code FOREIGN KEY (school_cd)
        REFERENCES common_code (code),
    CONSTRAINT chk_benefit_school_group CHECK (school_cd LIKE '0049%')
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='혜택 학력요건 매핑';


-- =====================================================================
--  19-4. benefit_job : 혜택 취업요건 매핑 (복합 PK)
-- =====================================================================
CREATE TABLE benefit_job
(
    benefit_no INT     NOT NULL COMMENT '혜택번호',
    job_cd     CHAR(7) NOT NULL COMMENT '취업요건코드(0013 계열)',
    PRIMARY KEY (benefit_no, job_cd),
    CONSTRAINT fk_benefit_job_benefit FOREIGN KEY (benefit_no)
        REFERENCES benefit (benefit_no),
    CONSTRAINT fk_benefit_job_code FOREIGN KEY (job_cd)
        REFERENCES common_code (code),
    CONSTRAINT chk_benefit_job_group CHECK (job_cd LIKE '0013%')
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='혜택 취업요건 매핑';


-- =====================================================================
--  20. applied_benefit : 신청 혜택 내역 (UNIQUE(member_no, benefit_no))
-- =====================================================================
CREATE TABLE applied_benefit
(
    applied_no INT      NOT NULL AUTO_INCREMENT COMMENT '신청혜택번호',
    member_no  INT      NOT NULL COMMENT '회원번호',
    benefit_no INT      NOT NULL COMMENT '혜택번호',
    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청일시',
    PRIMARY KEY (applied_no),
    CONSTRAINT uk_applied_benefit_member_benefit UNIQUE (member_no, benefit_no),
    CONSTRAINT fk_applied_benefit_member FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT fk_applied_benefit_benefit FOREIGN KEY (benefit_no)
        REFERENCES benefit (benefit_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='신청 혜택 내역';


-- =====================================================================
--  21. benefit_conflict_rule : 혜택 중복수혜 제한
--      UNIQUE(trigger_benefit_no, target_benefit_no)
--      trigger NULL = 외부 제도 경고 / 내부쌍은 trigger < target
-- =====================================================================
CREATE TABLE benefit_conflict_rule
(
    rule_no            INT          NOT NULL AUTO_INCREMENT COMMENT '규칙번호',
    trigger_benefit_no INT          NULL COMMENT '기준혜택번호(NULL=외부제도 경고)',
    target_benefit_no  INT          NOT NULL COMMENT '대상혜택번호',
    conflict_type      VARCHAR(20)  NOT NULL COMMENT '제한유형(중복불가/일부제한/확인필요)',
    rule_text          VARCHAR(500) NOT NULL COMMENT '규칙내용(표시 문구)',
    detection_type     VARCHAR(20)  NOT NULL DEFAULT '관리자입력' COMMENT '탐지유형(관리자입력/키워드자동탐지)',
    confirm_status     VARCHAR(20)  NOT NULL DEFAULT '검수필요' COMMENT '확정여부(확정/검수필요)',
    is_active          CHAR(1)      NOT NULL DEFAULT 'Y' COMMENT '활성화여부 Y/N',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    updated_at         DATETIME     NULL     DEFAULT NULL COMMENT '수정일시(MyBatis UPDATE로 갱신)',
    PRIMARY KEY (rule_no),
    CONSTRAINT uk_conflict_rule_pair UNIQUE (trigger_benefit_no, target_benefit_no),
    CONSTRAINT fk_conflict_rule_trigger FOREIGN KEY (trigger_benefit_no)
        REFERENCES benefit (benefit_no),
    CONSTRAINT fk_conflict_rule_target FOREIGN KEY (target_benefit_no)
        REFERENCES benefit (benefit_no),
    CONSTRAINT chk_conflict_rule_active CHECK (is_active IN ('Y', 'N')),
    CONSTRAINT chk_conflict_rule_order CHECK (trigger_benefit_no IS NULL OR trigger_benefit_no < target_benefit_no),
    CONSTRAINT chk_conflict_rule_type CHECK (conflict_type IN ('중복불가', '일부제한', '확인필요')),
    CONSTRAINT chk_conflict_rule_detect CHECK (detection_type IN ('관리자입력', '키워드자동탐지')),
    CONSTRAINT chk_conflict_rule_status CHECK (confirm_status IN ('확정', '검수필요'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='혜택 중복수혜 제한';


-- =====================================================================
--  22. sync_log : 동기화 로그
--      exec_type A=자동/M=수동, result_status S/P/F
-- =====================================================================
CREATE TABLE sync_log (
                          log_no        INT          NOT NULL AUTO_INCREMENT            COMMENT '로그번호',
                          executed_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '실행일시',
                          exec_type     ENUM('A','M') NOT NULL                         COMMENT '실행방식(A=자동/M=수동)',
                          sync_start_date DATE       NULL                              COMMENT '동기화 대상 등록일 시작(기간 미지정이면 NULL)',
                          sync_end_date   DATE       NULL                              COMMENT '동기화 대상 등록일 종료(기간 미지정이면 NULL)',
                          result_status ENUM('S','P','F') NOT NULL                     COMMENT '결과상태(성공/부분/실패)',
                          total_cnt     INT          NOT NULL DEFAULT 0                COMMENT '전체수집건수',
                          insert_cnt    INT          NOT NULL DEFAULT 0                COMMENT '신규추가건수',
                          update_cnt    INT          NOT NULL DEFAULT 0                COMMENT '업데이트건수',
                          skip_cnt      INT          NOT NULL DEFAULT 0                COMMENT '스킵건수',
                          delete_cnt    INT          NOT NULL DEFAULT 0                COMMENT 'API 응답에 없어 삭제 처리된 건수',
                          error_msg     VARCHAR(500) NULL                              COMMENT '오류내용(S면 보통 NULL)',
                          duration_ms   INT          NULL                              COMMENT '소요시간(ms)',
                          member_no     INT          NULL                              COMMENT '실행 관리자(A면 NULL, M이면 ADMIN member_no)',
                          PRIMARY KEY (log_no),
                          CONSTRAINT fk_sync_log_member FOREIGN KEY (member_no)
                              REFERENCES member (member_no),
                          CONSTRAINT chk_sync_log_counts CHECK (
                              total_cnt  >= 0 AND insert_cnt >= 0 AND update_cnt >= 0
                                  AND skip_cnt >= 0 AND delete_cnt >= 0
                                  AND (insert_cnt + update_cnt + skip_cnt + delete_cnt) <= total_cnt),
                          CONSTRAINT chk_sync_log_duration CHECK (duration_ms IS NULL OR duration_ms >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='동기화 로그';


-- ---------------------------------------------------------------------
-- 27. sync_log_detail : 동기화 처리 내역
--     sync_log는 '몇 건 처리했다'는 집계만 남기므로 어떤 혜택이 들어왔는지 알 수 없다.
--     관리자가 갱신 내역을 확인할 수 있도록 처리된 혜택을 건별로 기록한다.
--     동기화는 대상 혜택을 매번 덮어쓰므로 action_type='U'가 곧 값이 바뀌었다는 뜻은 아니다.
--     실제로 무엇이 달라졌는지는 changed_summary에 요약해 둔다.
-- ---------------------------------------------------------------------
CREATE TABLE sync_log_detail
(
    detail_no       INT          NOT NULL AUTO_INCREMENT COMMENT '상세번호',
    log_no          INT          NOT NULL COMMENT '동기화 실행 이력',
    benefit_no      INT          NOT NULL COMMENT '처리된 혜택',
    action_type     CHAR(1)      NOT NULL COMMENT '처리구분(I=신규/U=기존/D=API삭제)',
    changed_summary VARCHAR(500) NULL COMMENT '변경 내용 요약(신규거나 값이 그대로면 NULL)',
    PRIMARY KEY (detail_no),
    CONSTRAINT fk_sync_detail_log FOREIGN KEY (log_no)
        REFERENCES sync_log (log_no),
    CONSTRAINT fk_sync_detail_benefit FOREIGN KEY (benefit_no)
        REFERENCES benefit (benefit_no),
    CONSTRAINT chk_sync_detail_action CHECK (action_type IN ('I', 'U', 'D')),
    KEY idx_sync_detail_log (log_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='동기화 처리 내역';




-- ---------------------------------------------------------------------
-- ai_prompt  (v2.7)
--
--   AI 에게 주는 지시문을 코드가 아니라 DB 에서 관리한다.
--   프롬프트를 고칠 때마다 재배포하지 않기 위한 것이고,
--   버전을 남겨 문제가 생기면 이전 버전으로 되돌린다.
--
--   is_active='Y' 인 버전 하나만 실제로 사용된다.
--   조회에 실패하면 코드에 박힌 기본값으로 떨어지므로
--   이 테이블이 비어 있어도 AI 기능 자체는 동작한다.
-- ---------------------------------------------------------------------
CREATE TABLE ai_prompt
(
    prompt_no  INT          NOT NULL AUTO_INCREMENT COMMENT '프롬프트번호',
    prompt_key VARCHAR(50)  NOT NULL COMMENT '기능구분(CONFLICT_DETECTION 등)',
    version    INT          NOT NULL COMMENT '같은 key 안에서 올라가는 번호',
    content    TEXT         NOT NULL COMMENT '프롬프트 본문',
    memo       VARCHAR(200) NULL COMMENT '무엇을 왜 바꿨는지',
    is_active  CHAR(1)      NOT NULL DEFAULT 'N' COMMENT '실제 사용중인 버전(Y/N)',
    member_no  INT          NULL COMMENT '수정한 관리자(시드는 NULL)',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (prompt_no),
    CONSTRAINT uk_ai_prompt_version UNIQUE (prompt_key, version),
    CONSTRAINT chk_ai_prompt_active CHECK (is_active IN ('Y', 'N')),
    CONSTRAINT chk_ai_prompt_version CHECK (version > 0),
    KEY idx_ai_prompt_key_active (prompt_key, is_active)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 프롬프트 버전 관리';


-- ---------------------------------------------------------------------
-- benefit_conflict_analysis_run  (v2.8)
--
--   AI 를 한 번 부른 사실 자체를 남긴다.
--   응답이 비어 있어도, 실패해도 행이 생긴다.
--   「분석했는데 관계가 없었다」와 「분석을 못 했다」를 구분하기 위해서다.
--
--   is_current 를 CHAR(1) NULL 로 둔 이유
--     MySQL 은 UNIQUE 에서 NULL 중복을 허용한다.
--     그래서 (정책, 프롬프트, 세대) 당 현재 실행은 'Y' 하나만 존재하고
--     지난 실행은 NULL 로 얼마든지 쌓일 수 있다.
--     CHECK 로 'Y' 외의 값을 막아 두었다.
-- ---------------------------------------------------------------------
CREATE TABLE benefit_conflict_analysis_run
(
    run_no              INT          NOT NULL AUTO_INCREMENT COMMENT '실행번호',

    source_benefit_no   INT          NOT NULL COMMENT '분석한 정책',
    prompt_key          VARCHAR(50)  NOT NULL COMMENT 'CONFLICT_DETECTION 등',
    prompt_version      INT          NOT NULL COMMENT 'ai_prompt.version',
    source_text_hash    CHAR(64)     NOT NULL COMMENT '분석 대상 본문의 SHA-256',
    model_name          VARCHAR(50)  NULL COMMENT '분석에 쓴 모델',

    extraction_status   VARCHAR(30)  NOT NULL DEFAULT 'STARTED' COMMENT 'STARTED/OBSERVATIONS_READY/PARTIAL/FAILED. AI 재호출 판단용',
    canonical_status    VARCHAR(30)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/READY/FAILED/NOT_SELECTED. 실행 근거 사용 가능 여부',
    is_current          CHAR(1)      NULL COMMENT 'Y 또는 NULL. 현재 실행 세대',

    relations_extracted INT          NOT NULL DEFAULT 0 COMMENT 'AI 가 반환한 관계 수',
    observations_saved  INT          NOT NULL DEFAULT 0 COMMENT '관측 저장 성공',
    observations_failed INT          NOT NULL DEFAULT 0 COMMENT '관측 저장 실패',
    candidates_written  INT          NOT NULL DEFAULT 0 COMMENT '정리 결과로 쓴 후보 수',

    failure_reason      VARCHAR(500) NULL COMMENT '실패 사유',

    started_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        DATETIME     NULL,

    PRIMARY KEY (run_no),
    CONSTRAINT fk_run_source FOREIGN KEY (source_benefit_no)
        REFERENCES benefit (benefit_no),
    -- 현재 세대는 (정책, 프롬프트, 버전) 당 하나뿐이다. 지난 실행은 is_current NULL 로 쌓인다.
    CONSTRAINT uk_run_current UNIQUE (source_benefit_no, prompt_key, prompt_version, is_current),
    CONSTRAINT chk_run_extraction CHECK (extraction_status IN ('STARTED', 'OBSERVATIONS_READY', 'PARTIAL', 'FAILED')),
    CONSTRAINT chk_run_canonical CHECK (canonical_status IN ('PENDING', 'READY', 'FAILED', 'NOT_SELECTED')),
    CONSTRAINT chk_run_current CHECK (is_current = 'Y'),
    KEY idx_run_lookup (source_benefit_no, prompt_version, source_text_hash, extraction_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 중복수혜 분석 실행 이력';


-- ---------------------------------------------------------------------
-- benefit_conflict_observation  (v2.8)
--
--   AI 가 말한 관계를 정리하기 전 원본 그대로 남긴다.
--
--   dedupe_key 가 UNIQUE 가 아닌 이유
--     Candidate 쪽 dedupe_key 는 중복을 막는 열쇠지만
--     여기서는 같은 의미의 관측을 묶어 보기 위한 표시일 뿐이다.
--     한 실행 안에서 AI 가 같은 관계를 두 번 말하는 일이 실제로 있고,
--     그것도 관측이므로 지우지 않는다.
--
--   observation_index
--     AI 응답 배열 안에서의 순번이다.
--     (run_no, observation_index) 를 UNIQUE 로 묶어
--     같은 실행을 두 번 저장하는 사고를 막는다.
-- ---------------------------------------------------------------------
CREATE TABLE benefit_conflict_observation
(
    observation_no            INT           NOT NULL AUTO_INCREMENT COMMENT '관측번호',

    run_no                    INT           NOT NULL COMMENT '어느 실행에서 나왔는가',
    observation_index         INT           NOT NULL COMMENT 'AI 응답 안에서의 순번',
    source_benefit_no         INT           NOT NULL COMMENT '이 문장이 실린 정책',

    scope                     VARCHAR(20)   NOT NULL COMMENT 'OTHER_POLICY/SAME_POLICY/NOT_CONFLICT/UNCERTAIN/ERROR',
    relation                  VARCHAR(20)   NULL COMMENT 'FORBIDDEN/CONDITIONAL/ALLOWED',

    target_name_raw           VARCHAR(300)  NULL COMMENT '본문에 적힌 상대 정책명 그대로',
    target_category_raw       VARCHAR(300)  NULL COMMENT '범주로만 적혔을 때 그 표현 그대로',

    direction                 VARCHAR(20)   NULL COMMENT 'BIDIRECTIONAL/SOURCE_TO_TARGET/UNKNOWN',
    timing                    VARCHAR(20)   NULL COMMENT 'CURRENT/PAST/CURRENT_OR_PAST/UNKNOWN',
    subject_scope             VARCHAR(20)   NULL COMMENT 'APPLICANT/HOUSEHOLD/UNKNOWN',
    restriction_stage         VARCHAR(30)   NULL COMMENT 'APPLICATION/SELECTION/BENEFIT_RECEIPT/HISTORY/UNKNOWN',
    combination_applicability VARCHAR(10)   NULL COMMENT 'YES/NO/UNKNOWN',
    trigger_scope             VARCHAR(20)   NULL COMMENT 'APPLIED/APPROVED/CURRENT/PAST/UNKNOWN',

    condition_type            VARCHAR(30)   NULL COMMENT 'AI 가 말한 값 그대로',
    condition_text            VARCHAR(500)  NULL COMMENT '조건부일 때 그 조건 문장',

    evidence_text             TEXT          NULL COMMENT '판단 근거가 된 본문 문장 원문',
    evidence_verified         CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '추출한 이름이 근거 문장에 있는가',
    confidence                DECIMAL(4, 3) NULL COMMENT 'AI 자기보고. 판정에 쓰지 않는다',

    dedupe_key                VARCHAR(300)  NOT NULL COMMENT '의미 기반 묶음 키. UNIQUE 아님',

    created_at                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (observation_no),
    CONSTRAINT fk_observation_run FOREIGN KEY (run_no)
        REFERENCES benefit_conflict_analysis_run (run_no),
    CONSTRAINT fk_observation_source FOREIGN KEY (source_benefit_no)
        REFERENCES benefit (benefit_no),
    -- 같은 실행을 두 번 저장하는 사고를 막는다. 의미 중복은 막지 않는다.
    CONSTRAINT uk_observation_index UNIQUE (run_no, observation_index),
    CONSTRAINT chk_observation_scope CHECK (scope IN ('OTHER_POLICY', 'SAME_POLICY', 'NOT_CONFLICT', 'UNCERTAIN', 'ERROR')),
    CONSTRAINT chk_observation_evidence CHECK (evidence_verified IN ('Y', 'N')),
    KEY idx_observation_group (run_no, dedupe_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 가 반환한 원본 관계(정리 이전)';


-- ---------------------------------------------------------------------
-- benefit_conflict_candidate  (v2.6)
--
--   AI 가 공고문에서 찾아낸 중복수혜 관계를 담는다.
--   benefit_conflict_rule 과 역할이 다르다.
--
--     Candidate  공고문 자연어의 의미를 최대한 그대로 보존한다.
--                방향성, 과거이력, 가구원, 조건부처럼
--                현재 엔진이 표현하지 못하는 것도 여기서는 버리지 않는다.
--     Rule       현재 엔진이 실행할 수 있는 subset 만 담는다.
--
--   나중에 엔진이 확장되면 AI 를 다시 돌리지 않고
--   이 테이블만 다시 판정하면 된다.
--
--   상태값을 ENUM 이 아니라 VARCHAR 로 둔 이유
--     restriction_stage, combination_applicability 등은
--     실제 데이터를 보기 전에 최종값을 정할 수 없다고 결론이 났다.
--     값이 늘어날 때마다 ALTER 를 치지 않기 위해 문자열로 둔다.
-- ---------------------------------------------------------------------
CREATE TABLE benefit_conflict_candidate
(
    candidate_no              INT           NOT NULL AUTO_INCREMENT COMMENT '후보번호',

    source_benefit_no         INT           NOT NULL COMMENT '이 문장이 실린 정책',

    scope                     VARCHAR(20)   NOT NULL COMMENT 'OTHER_POLICY/SAME_POLICY/NOT_CONFLICT/UNCERTAIN',
    relation                  VARCHAR(20)   NULL COMMENT 'FORBIDDEN/CONDITIONAL/ALLOWED',

    target_name_raw           VARCHAR(300)  NULL COMMENT '본문에 적힌 상대 정책명 그대로',
    target_category_raw       VARCHAR(300)  NULL COMMENT '범주로만 적혔을 때 그 표현 그대로',
    category_code             VARCHAR(30)   NULL COMMENT '정규화 범주(1차 수집 단계에서는 비워둔다)',

    direction                 VARCHAR(20)   NULL COMMENT 'BIDIRECTIONAL/SOURCE_TO_TARGET/UNKNOWN',
    timing                    VARCHAR(20)   NULL COMMENT 'CURRENT/PAST/CURRENT_OR_PAST/UNKNOWN',
    subject_scope             VARCHAR(20)   NULL COMMENT 'APPLICANT/HOUSEHOLD/UNKNOWN',
    restriction_stage         VARCHAR(30)   NULL COMMENT 'APPLICATION/SELECTION/BENEFIT_RECEIPT/HISTORY',
    combination_applicability VARCHAR(10)   NULL COMMENT 'YES/NO/UNKNOWN. 신규 조합에도 적용할 근거가 있는가',
    trigger_scope             VARCHAR(20)   NULL COMMENT 'APPLIED/APPROVED/CURRENT/PAST. 현재는 Rule 로 승격하지 않는다',

    condition_type            VARCHAR(30)   NULL COMMENT 'AMOUNT_ADJUSTMENT/HISTORY_CONDITION/HOUSEHOLD_CONDITION/ELIGIBILITY_CONDITION',
    condition_text            VARCHAR(500)  NULL COMMENT '조건부일 때 그 조건 문장',

    evidence_text             TEXT          NULL COMMENT '판단 근거가 된 본문 문장 원문 그대로',
    evidence_verified         CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '추출한 이름이 근거 문장에 실제로 있는가',
    confidence                DECIMAL(4, 3) NULL COMMENT 'AI 자기보고. 자동확정 근거로 쓰지 않는다',

    mapped_benefit_no         INT           NULL COMMENT 'Resolver 가 찾은 상대 정책',
    resolver_result           VARCHAR(20)   NULL COMMENT 'UNIQUE_MATCH/MULTI_MATCH/NO_MATCH',
    resolver_candidates       VARCHAR(500)  NULL COMMENT 'MULTI 일 때 후보 benefit_no 목록',
    resolve_retry_cnt         INT           NOT NULL DEFAULT 0 COMMENT 'Resolver 재시도 횟수',
    last_resolved_at          DATETIME      NULL COMMENT '마지막 Resolver 재시도 시각',

    verifier_verdict          VARCHAR(20)   NULL COMMENT 'PASS/BLOCK/NOT_RUN',
    blocking_reasons          VARCHAR(500)  NULL COMMENT '쉼표 구분(DIRECTION_NOT_PROVEN 등)',
    crosscheck_result         VARCHAR(30)   NULL COMMENT 'MUTUAL/COUNTERPART_SILENT/COUNTERPART_ALLOWS/NOT_RUN',

    analysis_status           VARCHAR(20)   NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED/STALE',
    workflow_status           VARCHAR(20)   NOT NULL DEFAULT 'UNRESOLVED' COMMENT 'UNRESOLVED/REVIEW_REQUIRED/PENDING_DATA/DEFERRED/CONFIRMED/DISCARDED',
    enforcement_state         VARCHAR(20)   NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING_BLOCK/CONFIRMED_BLOCK/WARNING. 엔진이 보는 값',
    review_reason             VARCHAR(50)   NULL COMMENT '왜 검수로 왔는가(MULTI_MATCH/DIRECTION_UNKNOWN 등)',
    conflict_decision         VARCHAR(20)   NULL COMMENT '관리자 판정(BLOCK/PARTIAL/NOT_CONFLICT)',

    deferred_until            DATETIME      NULL COMMENT '보류 만료 시각. 지나면 다시 노출한다',
    discard_reason            VARCHAR(200)  NULL COMMENT '폐기 이유',
    decided_by                INT           NULL COMMENT '판정한 관리자',
    decided_at                DATETIME      NULL,

    model_name                VARCHAR(50)   NULL COMMENT '분석에 쓴 모델',
    prompt_key                VARCHAR(50)   NULL COMMENT 'CONFLICT_DETECTION 등',
    prompt_version            INT           NULL COMMENT 'ai_prompt.version',
    source_text_hash          CHAR(64)      NULL COMMENT '분석 대상 본문의 SHA-256. 같으면 재분석을 생략한다',

    dedupe_key                VARCHAR(300)  NOT NULL COMMENT '의미 기반 중복 방지 키',

    -- (v2.8) 관측 원본 보존층과 이어붙이는 컬럼.
    --        이 세 값은 정리 결과가 어느 실행에서 몇 건을 접은 것인지를 남긴다.
    canonical_run_no          INT           NULL COMMENT '이 후보를 만든 분석 실행',
    reconciliation_status     VARCHAR(20)   NULL COMMENT 'STABLE/CONFLICTING. 자동 판단에 쓰는 값에서 관측이 갈렸는가',
    observation_count         INT           NULL COMMENT '몇 건의 관측을 정리한 결과인가',

    created_at                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (candidate_no),
    CONSTRAINT fk_candidate_source FOREIGN KEY (source_benefit_no)
        REFERENCES benefit (benefit_no),
    CONSTRAINT fk_candidate_mapped FOREIGN KEY (mapped_benefit_no)
        REFERENCES benefit (benefit_no),
    -- 프롬프트나 원문이 바뀌면 새 행이 생겨야 이전 결과와 비교할 수 있으므로
    -- dedupe_key 단독이 아니라 세 값을 묶는다.
    CONSTRAINT uk_candidate_dedupe UNIQUE (dedupe_key, prompt_version, source_text_hash),
    CONSTRAINT chk_candidate_scope CHECK (scope IN ('OTHER_POLICY', 'SAME_POLICY', 'NOT_CONFLICT', 'UNCERTAIN', 'ERROR')),
    CONSTRAINT chk_candidate_analysis CHECK (analysis_status IN ('SUCCESS', 'FAILED', 'STALE')),
    CONSTRAINT chk_candidate_enforcement CHECK (enforcement_state IN ('NONE', 'PENDING_BLOCK', 'CONFIRMED_BLOCK', 'WARNING')),
    CONSTRAINT chk_candidate_evidence CHECK (evidence_verified IN ('Y', 'N')),
    -- (v2.8) 정리 이전 관측으로 되돌아갈 수 있게 실행을 가리킨다.
    CONSTRAINT fk_candidate_run FOREIGN KEY (canonical_run_no)
        REFERENCES benefit_conflict_analysis_run (run_no),
    CONSTRAINT chk_candidate_reconciliation CHECK (reconciliation_status IN ('STABLE', 'CONFLICTING')),
    KEY idx_candidate_source (source_benefit_no),
    KEY idx_candidate_mapped (mapped_benefit_no),
    KEY idx_candidate_enforce (enforcement_state),
    KEY idx_candidate_workflow (workflow_status, deferred_until),
    KEY idx_candidate_pending (workflow_status, resolver_result)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 중복수혜 분석 결과(실행 Rule 이전의 의미 보존층)';


-- ---------------------------------------------------------------------
-- trg_benefit_keep_admin_active  (v2.4)
--
--   관리자가 지정한 활성 상태를 동기화가 덮지 못하게 하고,
--   API가 같은 값으로 정상화되면 그 지정을 자동으로 해제한다.
--
--   is_active 는 동기화의 ON DUPLICATE KEY UPDATE 대상이라 매번 덮어쓰이므로,
--   관리자 지정은 admin_is_active 에, API 원본은 api_is_active 에 따로 둔다.
--   조회 쿼리는 기존대로 is_active 만 보면 되고 이 트리거가 값을 맞춘다.
--
--   admin_is_active 가 NULL 인 정책에는 아무 영향이 없다.
--   되돌리려면 : DROP TRIGGER trg_benefit_keep_admin_active;
-- ---------------------------------------------------------------------
DELIMITER $$

CREATE TRIGGER trg_benefit_keep_admin_active
    BEFORE UPDATE
    ON benefit
    FOR EACH ROW
BEGIN
    -- 관리자 지정은 API 상태값 오류에 대한 임시 개입이지 영구 숨김이 아니다.
    -- API가 관리자와 같은 판단에 도달했거나 정책 자체가 사라졌으면
    -- 개입할 이유가 없어졌으므로 지정을 해제한다.
    IF NEW.admin_is_active IS NOT NULL
        AND (NEW.admin_is_active = NEW.api_is_active
            OR NEW.api_deleted_yn = 'Y') THEN
        SET NEW.admin_is_active = NULL;
    END IF;

    -- 지정이 남아 있으면 동기화가 덮은 값을 되돌린다.
    IF NEW.admin_is_active IS NOT NULL THEN
        SET NEW.is_active = NEW.admin_is_active;
    END IF;
END$$

DELIMITER ;