-- =====================================================================
--  청년타파 (Youth-Tapa) - schema.sql
--  DBMS      : MySQL 8.0+ (InnoDB / utf8mb4)
--  기준      : DB 설계서(7/22 수정본) 테이블정의서 + ERD
--  컨벤션    : snake_case·단수형, 무접두사 / 제약 fk_·uk_·idx_ / ENUM 대문자
--  공통      : PK = PRIMARY KEY(고정) · created_at/updated_at/status(is_active)
--  참고      : 조건부 업무규칙(관리자 ROLE 검증, 시나리오별 필수값 등)은
--              서비스 계층에서 처리. 여기서는 정적·단순 규칙만 CHECK로 강제.
--  Soft Delete 설계이므로 FK는 CASCADE 없이 기본(RESTRICT) 유지.
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
SET FOREIGN_KEY_CHECKS = 1;


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
CREATE TABLE benefit_category (
    category_code CHAR(2)     NOT NULL                             COMMENT '카테고리코드',
    category_name VARCHAR(30) NOT NULL                             COMMENT '카테고리명',
    display_order INT         NULL                                 COMMENT '표시순서',
    PRIMARY KEY (category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='혜택카테고리';


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
    earn_min_amt        INT          NULL                          COMMENT '최소소득(earnMinAmt)',
    earn_max_amt        INT          NULL                          COMMENT '최대소득(earnMaxAmt)',
    mrg_stts_cd         VARCHAR(50)  NULL                          COMMENT '결혼유무코드(mrgSttsCd)',
    plcy_major_cd       VARCHAR(50)  NULL                          COMMENT '전공요건코드(plcyMajorCd)',
    school_cd           VARCHAR(50)  NULL                          COMMENT '학력요건코드(schoolCd)',
    job_cd              VARCHAR(50)  NULL                          COMMENT '취업요건코드(jobCd)',
    conflict_group_code VARCHAR(50)  NULL                          COMMENT '중복수혜그룹코드',
    inq_cnt             INT          NOT NULL DEFAULT 0            COMMENT '조회수(초기값; 실시간은 Redis)',
    is_active           CHAR(1)      NOT NULL DEFAULT 'Y'          COMMENT '활성화여부 Y/N(마감 경과 시 N)',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    updated_at          DATETIME     NULL     DEFAULT NULL         COMMENT '수정일',
    plcy_expln_cn       VARCHAR(300) NULL                          COMMENT '정책설명내용(plcyExplnCn)',
    PRIMARY KEY (benefit_no),
    CONSTRAINT uk_benefit_plcy_no UNIQUE (plcy_no),
    CONSTRAINT fk_benefit_category FOREIGN KEY (category_code)
        REFERENCES benefit_category (category_code),
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
    employ_status    ENUM('0013001','0013002','0013003','0013004','0013005','0013006','0013007','0013008','0013009') NULL
                                                                  COMMENT '취업상태(jobCd 0013 코드=라벨)',
    major            ENUM('0011001','0011002','0011003','0011004','0011005','0011006','0011007','0011008') NULL
                                                                  COMMENT '전공(plcyMajorCd 0011 코드=라벨)',
    household_size   TINYINT      NULL                            COMMENT '가구원수(1 이상)',
    education        ENUM('0049001','0049002','0049003','0049004','0049005','0049006','0049007','0049008','0049009') NULL
                                                                  COMMENT '학력(schoolCd 0049 코드=라벨)',
    is_married       CHAR(1)      NULL                            COMMENT '혼인여부 Y/N',
    profile_img_path VARCHAR(255) NULL                            COMMENT '프로필이미지경로(경로/URL만 저장)',
    updated_at       DATETIME     NULL DEFAULT NULL               COMMENT '수정일시(MyBatis UPDATE로 갱신)',
    PRIMARY KEY (member_no),
    CONSTRAINT fk_member_profile_member FOREIGN KEY (member_no)
        REFERENCES member (member_no),
    CONSTRAINT fk_member_profile_region FOREIGN KEY (region_code)
        REFERENCES region (region_code),
    CONSTRAINT chk_member_profile_married   CHECK (is_married IS NULL OR is_married IN ('Y','N')),
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
