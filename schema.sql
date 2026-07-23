-- =====================================================================
--  청년타파 (Youth-Tapa) - schema.sql
--  DBMS      : MySQL 8.0+ (InnoDB / utf8mb4)
--  기준      : DB 설계서(7/22 수정본) 테이블정의서 + ERD
--              + 7/23 1:00 PM 데이터 패치 (API 코드 테이블화)
--  컨벤션    : snake_case·단수형, 무접두사 / 제약 fk_·uk_·idx_ / ENUM 대문자
--  공통      : PK = PRIMARY KEY(고정) · created_at/updated_at/status(is_active)
--  참고      : 조건부 업무규칙(관리자 ROLE 검증, 시나리오별 필수값 등)은
--              서비스 계층에서 처리. 여기서는 정적·단순 규칙만 CHECK로 강제.
--  Soft Delete 설계이므로 FK는 CASCADE 없이 기본(RESTRICT) 유지.
--  ---------------------------------------------------------------------
--  [7/23 패치 요약] 총 24개 테이블 (기존 22 + common_code 1 + benefit_major 1)
--   · API 출처 코드(코드정보 탭 11개군 69건)를 ENUM -> common_code 통합
--     테이블 + FK로 전환. 코드군 검증은 CHECK (컬럼 LIKE '00NN%') 로 보완
--   · benefit : created_at->first_reg_dt, updated_at->last_mdfcn_dt(API 필드명),
--               earn_cnd_se_cd·earn_etc_cn 추가, plcy_major_cd 제거(다중값 분리)
--   · benefit_major 신규 : 전공요건 다중값("0011005,0011008") 정규화
--   · benefit_category : API 대분류(lclsfNm) 5종과 1:1, lclsf_nm 매칭키 추가
--     (대분류는 7자리 코드가 없어 common_code 에 포함하지 않고 별도 유지)
--   · goal_type/noti_type/role 등 자체 정의 고정값은 ENUM 유지(외부 코드 아님)
-- =====================================================================

-- CREATE DATABASE IF NOT EXISTS youthtapa
--   DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- USE youthtapa;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
--  초기화 (재실행 대비) — 자식 → 부모 역순 DROP
-- ---------------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS sync_log;
DROP TABLE IF EXISTS benefit_conflict_rule;
DROP TABLE IF EXISTS applied_benefit;
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
DROP TABLE IF EXISTS spending_category;
DROP TABLE IF EXISTS benefit_category;
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
--   · 정렬은 code 자체가 zero-padded 고정폭이라 ORDER BY code 로 충분해
--     display_order 는 두지 않는다.
-- =====================================================================
CREATE TABLE common_code (
    code       CHAR(7)     NOT NULL                                COMMENT '코드(전역 유일, 예: 0013004)',
    group_code CHAR(4)     NOT NULL                                COMMENT '코드군(예: 0013)',
    api_field  VARCHAR(30) NOT NULL                                COMMENT 'API 필드명(예: jobCd) - 동기화 매핑용',
    group_name VARCHAR(30) NOT NULL                                COMMENT '코드군명(예: 정책취업 요건코드)',
    code_name  VARCHAR(30) NOT NULL                                COMMENT '코드명(예: 프리랜서)',
    PRIMARY KEY (code),
    CONSTRAINT chk_common_code_group CHECK (code LIKE CONCAT(group_code, '%')),
    INDEX idx_common_code_group (group_code),
    INDEX idx_common_code_api_field (api_field)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='공통코드(온통청년 API 코드정보)';


-- =====================================================================
--  1. member : 회원
-- =====================================================================
CREATE TABLE member (
    member_no   INT           NOT NULL AUTO_INCREMENT              COMMENT '회원번호',
    login_id    VARCHAR(30)   NOT NULL                             COMMENT '아이디',
    password    VARCHAR(255)  NOT NULL                             COMMENT '비밀번호(BCrypt 해시)',
    email       VARCHAR(100)  NOT NULL                             COMMENT '이메일',
    role        ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER'       COMMENT '권한',
    real_name   VARCHAR(20)   NOT NULL                             COMMENT '실명(본인확인용)',
    status      CHAR(1)       NOT NULL DEFAULT 'Y'                 COMMENT '회원상태 Y/N(탈퇴 시 N, Soft Delete)',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '가입일시',
    updated_at  DATETIME      NULL     DEFAULT NULL                COMMENT '수정일시(MyBatis UPDATE로 갱신)',
    PRIMARY KEY (member_no),
    CONSTRAINT uk_member_login_id UNIQUE (login_id),
    CONSTRAINT uk_member_email    UNIQUE (email),
    CONSTRAINT chk_member_status  CHECK (status IN ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원';


-- =====================================================================
--  2. terms : 약관
-- =====================================================================
CREATE TABLE terms (
    terms_no    INT          NOT NULL AUTO_INCREMENT               COMMENT '약관번호',
    content     TEXT         NOT NULL                              COMMENT '약관내용',
    is_required CHAR(1)      NOT NULL                              COMMENT '필수여부 Y/N',
    version     CHAR(3)      NOT NULL                              COMMENT '버전(예: v10)',
    PRIMARY KEY (terms_no),
    CONSTRAINT chk_terms_is_required CHECK (is_required IN ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='약관';


-- =====================================================================
--  3. region : 지역 (법정시군구코드, 자기참조)
-- =====================================================================
CREATE TABLE region (
    region_code        CHAR(5)     NOT NULL                        COMMENT '지역코드(법정시군구코드)',
    region_name        VARCHAR(50) NOT NULL                        COMMENT '지역명',
    parent_region_code CHAR(5)     NULL                            COMMENT '상위지역코드(시/도·전국은 NULL)',
    PRIMARY KEY (region_code),
    CONSTRAINT fk_region_parent FOREIGN KEY (parent_region_code)
        REFERENCES region (region_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='지역';


-- =====================================================================
--  4. benefit_category : 혜택카테고리
-- =====================================================================
--  · API 대분류(lclsfNm) 5종과 1:1. 폴백(99 미분류) 미사용 →
--    미정의 대분류 수신 시 동기화 로직에서 예외 처리 필요.
--  · API 응답에는 대분류 '코드'가 없고 이름 문자열만 오므로 lclsf_nm 을
--    매칭 키로 사용. 파일 표기("교육")와 실제 응답("교육･직업훈련")이
--    다르고 가운뎃점도 반각(U+FF65)이라, 반드시 응답 원문을 저장할 것.
CREATE TABLE benefit_category (
    category_code CHAR(2)     NOT NULL                             COMMENT '카테고리코드(01~05)',
    category_name VARCHAR(30) NOT NULL                             COMMENT '카테고리명(화면 표시용)',
    lclsf_nm      VARCHAR(50) NOT NULL                             COMMENT '정책대분류명(lclsfNm, API 원문·동기화 매칭키)',
    display_order INT         NULL                                 COMMENT '표시순서',
    PRIMARY KEY (category_code),
    CONSTRAINT uk_benefit_category_lclsf_nm UNIQUE (lclsf_nm)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='혜택카테고리(API 대분류 1:1)';


-- =====================================================================
--  5. spending_category : 소비 카테고리
-- =====================================================================
CREATE TABLE spending_category (
    category_no   INT         NOT NULL AUTO_INCREMENT              COMMENT '카테고리번호',
    category_name VARCHAR(30) NOT NULL                            COMMENT '카테고리명',
    PRIMARY KEY (category_no),
    CONSTRAINT uk_spending_category_name UNIQUE (category_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='소비 카테고리';


-- =====================================================================
--  6. finance_product : 금융 상품
-- =====================================================================
CREATE TABLE finance_product (
    product_no    INT          NOT NULL AUTO_INCREMENT             COMMENT '상품번호',
    product_name  VARCHAR(100) NOT NULL                           COMMENT '상품명',
    product_type  ENUM('DEPOSIT','SAVINGS','SUBSCRIPTION','INSURANCE','PENSION') NOT NULL
                                                                  COMMENT '상품유형(예금/적금/청약/보험·공제/퇴직연금)',
    org_name      VARCHAR(100) NOT NULL                           COMMENT '금융기관명',
    interest_rate DECIMAL(5,2) NULL                               COMMENT '연이율',
    PRIMARY KEY (product_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='금융 상품';


-- =====================================================================
--  7. recommend_keyword : 추천검색어
-- =====================================================================
CREATE TABLE recommend_keyword (
    keyword_code  INT         NOT NULL AUTO_INCREMENT              COMMENT '키워드코드',
    keyword_name  VARCHAR(50) NOT NULL                            COMMENT '키워드명',
    display_order INT         NULL                                COMMENT '표시순서',
    is_active     CHAR(1)     NOT NULL DEFAULT 'Y'                COMMENT '활성화여부 Y/N',
    PRIMARY KEY (keyword_code),
    CONSTRAINT chk_recommend_keyword_active CHECK (is_active IN ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='추천검색어';


-- =====================================================================
--  8. stress_scenario : 스트레스 테스트 시나리오
--     복합 UNIQUE(scenario_code, shock_level)
-- =====================================================================
CREATE TABLE stress_scenario (
    scenario_no     INT          NOT NULL AUTO_INCREMENT           COMMENT '시나리오번호',
    scenario_code   VARCHAR(20)  NOT NULL                         COMMENT '시나리오코드(INFLATION/MEDICAL/RENT/RATE/COMPLEX)',
    scenario_name   VARCHAR(50)  NOT NULL                         COMMENT '시나리오명',
    description     VARCHAR(200) NULL                             COMMENT '설명',
    shock_level     ENUM('LOW','MID','HIGH') NOT NULL             COMMENT '충격강도',
    target_category VARCHAR(100) NOT NULL                         COMMENT '영향카테고리(COMPLEX는 ALL)',
    change_rate     DECIMAL(4,3) NULL                             COMMENT '변동률(0~1, 0.100=10%)',
    fixed_amount    BIGINT       NULL                             COMMENT '고정추가금액(원)',
    PRIMARY KEY (scenario_no),
    CONSTRAINT uk_stress_scenario_code_level UNIQUE (scenario_code, shock_level),
    CONSTRAINT chk_stress_change_rate  CHECK (change_rate IS NULL OR (change_rate >= 0 AND change_rate <= 1)),
    CONSTRAINT chk_stress_fixed_amount CHECK (fixed_amount IS NULL OR fixed_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='스트레스 테스트 시나리오';


-- =====================================================================
--  9. benefit : 청년지원혜택 (온통청년 API 매핑)
-- =====================================================================
CREATE TABLE benefit (
    benefit_no          INT          NOT NULL AUTO_INCREMENT       COMMENT '혜택번호(내부 PK)',
    plcy_no             VARCHAR(30)  NULL                          COMMENT '외부정책ID(plcyNo, 동기화 매칭키)',
    plcy_nm             VARCHAR(100) NOT NULL                      COMMENT '혜택명(plcyNm)',
    category_code       CHAR(2)      NOT NULL                      COMMENT '카테고리코드',
    sprvsn_inst_cd_nm   VARCHAR(100) NULL                          COMMENT '주관기관명(sprvsnInstCdNm)',
    target_desc         TEXT         NULL                          COMMENT '지원대상',
    plcy_sprt_cn        TEXT         NULL                          COMMENT '지원내용(plcySprtCn)',
    support_amount      INT          NULL                          COMMENT '지원금액(파싱)',
    plcy_aply_mthd_cn   VARCHAR(500) NULL                          COMMENT '신청방법(plcyAplyMthdCn)',
    sbmsn_dcmnt_cn      VARCHAR(500) NULL                          COMMENT '제출서류(sbmsnDcmntCn)',
    apply_start_date    DATE         NULL                          COMMENT '신청시작일',
    apply_end_date      DATE         NULL                          COMMENT '신청종료일(D-Day 기준)',
    aply_ymd            VARCHAR(200) NULL                          COMMENT '신청기간원문(aplyYmd)',
    aply_url_addr       VARCHAR(255) NULL                          COMMENT '신청URL(aplyUrlAddr)',
    sprt_trgt_min_age   INT          NULL                          COMMENT '최소연령(sprtTrgtMinAge)',
    sprt_trgt_max_age   INT          NULL                          COMMENT '최대연령(sprtTrgtMaxAge)',
    earn_cnd_se_cd      CHAR(7)      NULL                          COMMENT '소득조건구분코드(earnCndSeCd) 0043',
    earn_min_amt        INT          NULL                          COMMENT '최소소득(earnMinAmt)',
    earn_max_amt        INT          NULL                          COMMENT '최대소득(earnMaxAmt)',
    earn_etc_cn         TEXT         NULL                          COMMENT '소득기타내용(earnEtcCn, 0043003일 때 조건 원문)',
    mrg_stts_cd         CHAR(7)      NULL                          COMMENT '결혼상태코드(mrgSttsCd) 0055',
    school_cd           CHAR(7)      NULL                          COMMENT '학력요건코드(schoolCd) 0049',
    job_cd              CHAR(7)      NULL                          COMMENT '취업요건코드(jobCd) 0013',
    conflict_group_code VARCHAR(50)  NULL                          COMMENT '중복수혜그룹코드',
    inq_cnt             INT          NOT NULL DEFAULT 0            COMMENT '조회수(초기값; 실시간은 Redis)',
    is_active           CHAR(1)      NOT NULL DEFAULT 'Y'          COMMENT '활성화여부 Y/N(마감 경과 시 N)',
    first_reg_dt        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초등록일시(frstRegDt)',
    last_mdfcn_dt       DATETIME     NULL     DEFAULT NULL         COMMENT '최종수정일시(lastMdfcnDt)',
    plcy_expln_cn       VARCHAR(300) NULL                          COMMENT '정책설명내용(plcyExplnCn)',
    PRIMARY KEY (benefit_no),
    CONSTRAINT uk_benefit_plcy_no UNIQUE (plcy_no),
    CONSTRAINT fk_benefit_category FOREIGN KEY (category_code)
        REFERENCES benefit_category (category_code),
    CONSTRAINT fk_benefit_job      FOREIGN KEY (job_cd)         REFERENCES common_code (code),
    CONSTRAINT fk_benefit_school   FOREIGN KEY (school_cd)      REFERENCES common_code (code),
    CONSTRAINT fk_benefit_mrg      FOREIGN KEY (mrg_stts_cd)    REFERENCES common_code (code),
    CONSTRAINT fk_benefit_earn_cnd FOREIGN KEY (earn_cnd_se_cd) REFERENCES common_code (code),
    -- FK는 코드 존재만 검증하므로 코드군까지 CHECK로 강제 (NULL은 UNKNOWN이라 통과)
    -- 혜택 측은 '제한없음'이 정상 조건값이므로 별도 차단하지 않는다
    CONSTRAINT chk_benefit_job      CHECK (job_cd         LIKE '0013%'),
    CONSTRAINT chk_benefit_school   CHECK (school_cd      LIKE '0049%'),
    CONSTRAINT chk_benefit_mrg      CHECK (mrg_stts_cd    LIKE '0055%'),
    CONSTRAINT chk_benefit_earn_cnd CHECK (earn_cnd_se_cd LIKE '0043%'),
    CONSTRAINT chk_benefit_is_active CHECK (is_active IN ('Y','N')),
    INDEX idx_benefit_apply_end (apply_end_date),
    INDEX idx_benefit_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='청년지원혜택';


-- =====================================================================
--  10. member_profile : 회원 프로필 (member와 1:1, member_no = PK+FK)
-- =====================================================================
CREATE TABLE member_profile (
    member_no        INT          NOT NULL                        COMMENT '회원번호(PK이자 FK, 자동채번 아님)',
    birth_date       DATE         NULL                            COMMENT '생년월일(나이 자격 판정)',
    region_code      CHAR(5)      NULL                            COMMENT '지역코드',
    income           INT          NULL                            COMMENT '소득(실제값)',
    employ_status    CHAR(7)      NULL                            COMMENT '취업상태(jobCd 0013)',
    major            CHAR(7)      NULL                            COMMENT '전공(plcyMajorCd 0011)',
    household_size   TINYINT      NULL                            COMMENT '가구원수(1 이상)',
    education        CHAR(7)      NULL                            COMMENT '학력(schoolCd 0049)',
    mrg_stts_cd      CHAR(7)      NULL                            COMMENT '결혼상태(mrgSttsCd 0055)',
    profile_img_path VARCHAR(255) NULL                            COMMENT '프로필이미지경로(경로/URL만 저장)',
    updated_at       DATETIME     NULL DEFAULT NULL               COMMENT '수정일시(MyBatis UPDATE로 갱신)',
    PRIMARY KEY (member_no),
    CONSTRAINT fk_member_profile_member FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT fk_member_profile_region FOREIGN KEY (region_code)
        REFERENCES region (region_code),
    CONSTRAINT fk_member_profile_job    FOREIGN KEY (employ_status) REFERENCES common_code (code),
    CONSTRAINT fk_member_profile_major  FOREIGN KEY (major)         REFERENCES common_code (code),
    CONSTRAINT fk_member_profile_school FOREIGN KEY (education)     REFERENCES common_code (code),
    CONSTRAINT fk_member_profile_mrg    FOREIGN KEY (mrg_stts_cd)   REFERENCES common_code (code),
    -- LIKE : 통합 코드테이블이라 FK가 코드군을 못 보므로 코드군을 강제
    -- <>   : '제한없음'은 혜택의 조건값일 뿐 사람의 상태가 될 수 없으므로 회원 측만 차단
    -- NULL은 CHECK 평가 결과가 UNKNOWN이라 통과 → 프로필 미입력 허용과 충돌하지 않음
    CONSTRAINT chk_member_profile_job    CHECK (employ_status LIKE '0013%' AND employ_status <> '0013010'),
    CONSTRAINT chk_member_profile_major  CHECK (major         LIKE '0011%' AND major         <> '0011009'),
    CONSTRAINT chk_member_profile_school CHECK (education     LIKE '0049%' AND education     <> '0049010'),
    CONSTRAINT chk_member_profile_mrg    CHECK (mrg_stts_cd   LIKE '0055%' AND mrg_stts_cd   <> '0055003'),
    CONSTRAINT chk_member_profile_household CHECK (household_size IS NULL OR household_size >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원 프로필';


-- =====================================================================
--  11. member_terms_agree : 회원 약관동의
-- =====================================================================
CREATE TABLE member_terms_agree (
    agree_no   INT      NOT NULL AUTO_INCREMENT                    COMMENT '동의번호',
    member_no  INT      NOT NULL                                  COMMENT '회원번호',
    terms_no   INT      NOT NULL                                  COMMENT '약관번호',
    is_agreed  CHAR(1)  NOT NULL                                  COMMENT '동의여부 Y/N',
    agreed_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP        COMMENT '동의일시',
    PRIMARY KEY (agree_no),
    CONSTRAINT fk_member_terms_agree_member FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT fk_member_terms_agree_terms  FOREIGN KEY (terms_no)
        REFERENCES terms (terms_no),
    CONSTRAINT chk_member_terms_agree CHECK (is_agreed IN ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원 약관동의';


-- =====================================================================
--  12. goal : 목표
-- =====================================================================
CREATE TABLE goal (
    goal_no    INT      NOT NULL AUTO_INCREMENT                    COMMENT '목표번호',
    member_no  INT      NOT NULL                                  COMMENT '회원번호',
    goal_type  ENUM('INDEPENDENCE','EMPLOYMENT','STARTUP','MARRIAGE','STUDY_ABROAD') NOT NULL
                                                                  COMMENT '목표유형(독립/취업/창업/결혼/유학)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP        COMMENT '설정일시',
    PRIMARY KEY (goal_no),
    CONSTRAINT fk_goal_member FOREIGN KEY (member_no)
        REFERENCES member (member_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='목표';


-- =====================================================================
--  13. notification : 알림
-- =====================================================================
CREATE TABLE notification (
    noti_no    INT      NOT NULL AUTO_INCREMENT                    COMMENT '알림번호',
    member_no  INT      NOT NULL                                  COMMENT '회원번호',
    noti_type  ENUM('DEADLINE','SPENDING','NEW_BENEFIT','ACCOUNT') NOT NULL
                                                                  COMMENT '알림유형(마감임박/소비분석/신규혜택/계정·정보수정)',
    content    TEXT     NOT NULL                                  COMMENT '내용',
    is_read    CHAR(1)  NOT NULL DEFAULT 'N'                      COMMENT '읽음여부 Y/N',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP        COMMENT '생성일시',
    PRIMARY KEY (noti_no),
    CONSTRAINT fk_notification_member FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT chk_notification_is_read CHECK (is_read IN ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='알림';


-- =====================================================================
--  14. account : 계좌
-- =====================================================================
CREATE TABLE account (
    account_id INT         NOT NULL AUTO_INCREMENT                 COMMENT '식별번호',
    member_no  INT         NOT NULL                               COMMENT '회원번호',
    bank_name  VARCHAR(50) NOT NULL                               COMMENT '은행명',
    account_no VARCHAR(30) NOT NULL                               COMMENT '계좌번호',
    balance    BIGINT      NOT NULL DEFAULT 0                     COMMENT '잔액',
    PRIMARY KEY (account_id),
    CONSTRAINT uk_account_no UNIQUE (account_no),
    CONSTRAINT fk_account_member FOREIGN KEY (member_no)
        REFERENCES member (member_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='계좌';


-- =====================================================================
--  15. member_finance_product : 사용자 금융 상품
-- =====================================================================
CREATE TABLE member_finance_product (
    link_no       INT    NOT NULL AUTO_INCREMENT                  COMMENT '연결번호',
    member_no     INT    NOT NULL                                COMMENT '회원번호',
    product_no    INT    NOT NULL                                COMMENT '상품번호',
    hold_amount   BIGINT NOT NULL DEFAULT 0                      COMMENT '보유금액',
    join_date     DATE   NULL                                    COMMENT '가입일',
    maturity_date DATE   NULL                                    COMMENT '만기일',
    PRIMARY KEY (link_no),
    CONSTRAINT fk_member_finance_product_member  FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT fk_member_finance_product_product FOREIGN KEY (product_no)
        REFERENCES finance_product (product_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자 금융 상품';


-- =====================================================================
--  16. spending : 소비 내역
-- =====================================================================
CREATE TABLE spending (
    spending_no   INT          NOT NULL AUTO_INCREMENT            COMMENT '소비번호',
    account_id    INT          NOT NULL                          COMMENT '식별번호(계좌)',
    member_no     INT          NOT NULL                          COMMENT '회원번호',
    category_no   INT          NOT NULL                          COMMENT '카테고리번호',
    spending_date DATE         NOT NULL                          COMMENT '소비일자',
    amount        BIGINT       NOT NULL                          COMMENT '소비금액',
    merchant      VARCHAR(100) NULL                              COMMENT '사용처',
    pay_method    ENUM('CASH','CHECK_CARD','CREDIT_CARD','EASY_PAY','TRANSFER') NOT NULL
                                                                 COMMENT '결제수단(현금/체크카드/신용카드/간편결제/계좌이체)',
    memo          VARCHAR(200) NULL                              COMMENT '메모',
    PRIMARY KEY (spending_no),
    CONSTRAINT fk_spending_account  FOREIGN KEY (account_id)
        REFERENCES account (account_id),
    CONSTRAINT fk_spending_member   FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT fk_spending_category FOREIGN KEY (category_no)
        REFERENCES spending_category (category_no),
    INDEX idx_spending_member_date (member_no, spending_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='소비 내역';


-- =====================================================================
--  17. expected_spending : 예상 소비
-- =====================================================================
CREATE TABLE expected_spending (
    expected_no     INT          NOT NULL AUTO_INCREMENT          COMMENT '예상소비번호',
    account_id      INT          NOT NULL                        COMMENT '식별번호(계좌)',
    member_no       INT          NOT NULL                        COMMENT '회원번호',
    category_no     INT          NOT NULL                        COMMENT '카테고리번호',
    expected_date   DATE         NOT NULL                        COMMENT '소비예정일',
    expected_amount BIGINT       NOT NULL                        COMMENT '예상금액',
    merchant        VARCHAR(100) NULL                            COMMENT '사용처',
    memo            VARCHAR(200) NULL                            COMMENT '메모',
    PRIMARY KEY (expected_no),
    CONSTRAINT fk_expected_spending_account  FOREIGN KEY (account_id)
        REFERENCES account (account_id),
    CONSTRAINT fk_expected_spending_member   FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT fk_expected_spending_category FOREIGN KEY (category_no)
        REFERENCES spending_category (category_no),
    INDEX idx_expected_spending_member_date (member_no, expected_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='예상 소비';


-- =====================================================================
--  18. favorite_benefit : 관심혜택  (UNIQUE(member_no, benefit_no))
-- =====================================================================
CREATE TABLE favorite_benefit (
    favorite_no INT      NOT NULL AUTO_INCREMENT                  COMMENT '관심혜택번호',
    member_no   INT      NOT NULL                                COMMENT '회원번호',
    benefit_no  INT      NOT NULL                                COMMENT '혜택번호',
    saved_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT '저장일시',
    PRIMARY KEY (favorite_no),
    CONSTRAINT uk_favorite_benefit_member_benefit UNIQUE (member_no, benefit_no),
    CONSTRAINT fk_favorite_benefit_member  FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT fk_favorite_benefit_benefit FOREIGN KEY (benefit_no)
        REFERENCES benefit (benefit_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관심혜택';


-- =====================================================================
--  19. benefit_region : 혜택지역 매핑 (복합 PK)
-- =====================================================================
CREATE TABLE benefit_region (
    benefit_no  INT     NOT NULL                                  COMMENT '혜택번호',
    region_code CHAR(5) NOT NULL                                  COMMENT '지역코드',
    PRIMARY KEY (benefit_no, region_code),
    CONSTRAINT fk_benefit_region_benefit FOREIGN KEY (benefit_no)
        REFERENCES benefit (benefit_no),
    CONSTRAINT fk_benefit_region_region  FOREIGN KEY (region_code)
        REFERENCES region (region_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='혜택지역 매핑';


-- =====================================================================
--  19-2. benefit_major : 혜택 전공요건 매핑 (복합 PK)
--   · API가 plcyMajorCd를 "0011005,0011008" 처럼 다중값으로 응답하므로
--     한 컬럼에 담지 않고 benefit_region 과 동일한 패턴으로 정규화
-- =====================================================================
CREATE TABLE benefit_major (
    benefit_no INT     NOT NULL                                    COMMENT '혜택번호',
    major_code CHAR(7) NOT NULL                                    COMMENT '전공요건코드(0011)',
    PRIMARY KEY (benefit_no, major_code),
    CONSTRAINT fk_benefit_major_benefit FOREIGN KEY (benefit_no)
        REFERENCES benefit (benefit_no),
    CONSTRAINT fk_benefit_major_major   FOREIGN KEY (major_code)
        REFERENCES common_code (code),
    CONSTRAINT chk_benefit_major_group CHECK (major_code LIKE '0011%')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='혜택 전공요건 매핑';


-- =====================================================================
--  20. applied_benefit : 신청 혜택 내역 (UNIQUE(member_no, benefit_no))
-- =====================================================================
CREATE TABLE applied_benefit (
    applied_no INT      NOT NULL AUTO_INCREMENT                   COMMENT '신청혜택번호',
    member_no  INT      NOT NULL                                 COMMENT '회원번호',
    benefit_no INT      NOT NULL                                 COMMENT '혜택번호',
    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP       COMMENT '신청일시',
    PRIMARY KEY (applied_no),
    CONSTRAINT uk_applied_benefit_member_benefit UNIQUE (member_no, benefit_no),
    CONSTRAINT fk_applied_benefit_member  FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT fk_applied_benefit_benefit FOREIGN KEY (benefit_no)
        REFERENCES benefit (benefit_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='신청 혜택 내역';


-- =====================================================================
--  21. benefit_conflict_rule : 혜택 중복수혜 제한
--      UNIQUE(trigger_benefit_no, target_benefit_no)
--      trigger NULL = 외부 제도 경고 / 내부쌍은 trigger < target
-- =====================================================================
CREATE TABLE benefit_conflict_rule (
    rule_no            INT          NOT NULL AUTO_INCREMENT        COMMENT '규칙번호',
    trigger_benefit_no INT          NULL                          COMMENT '기준혜택번호(NULL=외부제도 경고)',
    target_benefit_no  INT          NOT NULL                      COMMENT '대상혜택번호',
    conflict_type      VARCHAR(20)  NOT NULL                      COMMENT '제한유형(중복불가/일부제한/확인필요)',
    rule_text          VARCHAR(500) NOT NULL                      COMMENT '규칙내용(표시 문구)',
    detection_type     VARCHAR(20)  NOT NULL DEFAULT '관리자입력'  COMMENT '탐지유형(관리자입력/키워드자동탐지)',
    confirm_status     VARCHAR(20)  NOT NULL DEFAULT '검수필요'    COMMENT '확정여부(확정/검수필요)',
    is_active          CHAR(1)      NOT NULL DEFAULT 'Y'          COMMENT '활성화여부 Y/N',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    updated_at         DATETIME     NULL     DEFAULT NULL         COMMENT '수정일시(MyBatis UPDATE로 갱신)',
    PRIMARY KEY (rule_no),
    CONSTRAINT uk_conflict_rule_pair UNIQUE (trigger_benefit_no, target_benefit_no),
    CONSTRAINT fk_conflict_rule_trigger FOREIGN KEY (trigger_benefit_no)
        REFERENCES benefit (benefit_no),
    CONSTRAINT fk_conflict_rule_target  FOREIGN KEY (target_benefit_no)
        REFERENCES benefit (benefit_no),
    CONSTRAINT chk_conflict_rule_active CHECK (is_active IN ('Y','N')),
    CONSTRAINT chk_conflict_rule_order  CHECK (trigger_benefit_no IS NULL OR trigger_benefit_no < target_benefit_no),
    CONSTRAINT chk_conflict_rule_type   CHECK (conflict_type   IN ('중복불가','일부제한','확인필요')),
    CONSTRAINT chk_conflict_rule_detect CHECK (detection_type  IN ('관리자입력','키워드자동탐지')),
    CONSTRAINT chk_conflict_rule_status CHECK (confirm_status  IN ('확정','검수필요'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='혜택 중복수혜 제한';


-- =====================================================================
--  22. sync_log : 동기화 로그
--      exec_type A=자동/M=수동, result_status S/P/F
-- =====================================================================
CREATE TABLE sync_log (
    log_no        INT          NOT NULL AUTO_INCREMENT            COMMENT '로그번호',
    executed_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '실행일시',
    exec_type     ENUM('A','M') NOT NULL                         COMMENT '실행방식(A=자동/M=수동)',
    result_status ENUM('S','P','F') NOT NULL                     COMMENT '결과상태(성공/부분/실패)',
    total_cnt     INT          NOT NULL DEFAULT 0                COMMENT '전체수집건수',
    insert_cnt    INT          NOT NULL DEFAULT 0                COMMENT '신규추가건수',
    update_cnt    INT          NOT NULL DEFAULT 0                COMMENT '업데이트건수',
    skip_cnt      INT          NOT NULL DEFAULT 0                COMMENT '스킵건수',
    error_msg     VARCHAR(500) NULL                              COMMENT '오류내용(S면 보통 NULL)',
    duration_ms   INT          NULL                              COMMENT '소요시간(ms)',
    member_no     INT          NULL                              COMMENT '실행 관리자(A면 NULL, M이면 ADMIN member_no)',
    PRIMARY KEY (log_no),
    CONSTRAINT fk_sync_log_member FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT chk_sync_log_counts CHECK (
        total_cnt  >= 0 AND insert_cnt >= 0 AND update_cnt >= 0 AND skip_cnt >= 0
        AND (insert_cnt + update_cnt + skip_cnt) <= total_cnt),
    CONSTRAINT chk_sync_log_duration CHECK (duration_ms IS NULL OR duration_ms >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='동기화 로그';
