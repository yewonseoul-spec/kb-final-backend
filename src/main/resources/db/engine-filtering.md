# 추천 엔진(engine) 필터링 과정

> 담당: 박상호 · 최종수정: 2026-08-07
> 엔진이 정책 2,700건에서 어떻게 3개를 골라내는지 설명한다.
> 각 단계마다 실제 쿼리와 그 뜻을 함께 적었다.

---

## 0. 전체 흐름

```
전체 정책 약 2,700건
      │
      │  ① 자격조건 필터        나이·소득·지역·학력·전공·취업·혼인
      ▼
   후보 39건
      │
      │  ② 중복수혜 검사        이미 받는 것과 겹치면 제거
      ▼
   상위 20건                    ③ 점수 매겨 K개만 남김
      │
      │  ④ 조합 만들기          후보끼리 충돌하면 그 조합은 버림
      ▼
  추천 조합 1개 (정책 3개)
```

건수는 회원 2번(경기도, 28세, 월소득 207만원) 기준 실측값이다.
전체 정책 수는 동기화 시점마다 달라진다.

**①②는 SQL 한 번에 끝난다.** `EngineMapper.xml`의 `findEligibleBenefits` 하나가 다 한다.
**③④는 자바에서 한다.** `EngineServiceImpl`이다.

---

# 1단계 · 자격조건 필터

## 마감된 정책 걸러내기

```sql
WHERE b.is_active = 'Y'
AND (b.aply_prd_se_cd IS NULL OR b.aply_prd_se_cd <> '0057003')
AND (b.apply_end_date IS NULL OR b.apply_end_date >= CURDATE())
```

**세 겹으로 막는다.**

| 조건 | 뜻 |
| --- | --- |
| `is_active = 'Y'` | 동기화할 때 계산해둔 노출 여부 |
| `aply_prd_se_cd <> '0057003'` | 신청기간 구분이 '마감'이 아닐 것 |
| `apply_end_date >= CURDATE()` | 마감일이 오늘 이후일 것 |

**왜 세 개나 필요한가?**

`is_active`는 동기화 시점에 계산된 값이다. 그 뒤에 마감된 정책은 갱신이 안 된다. 그래서 조회할 때마다 날짜를 다시 본다.

그런데 날짜만으로는 부족하다. **신청기간 구분이 '마감'인 정책은 마감일이 NULL로 들어온다.** `IS NULL OR` 때문에 그대로 통과해버린다. 정책명에 '[7월 마감]'이 적혀 있는데도 후보에 남던 사례가 실제로 있었다.

그래서 마감 코드를 직접 차단하는 조건을 하나 더 뒀다.

---

## 나이

```sql
AND (b.sprt_trgt_min_age IS NULL
     OR TIMESTAMPDIFF(YEAR, #{birthDate}, CURDATE()) >= b.sprt_trgt_min_age)
AND (b.sprt_trgt_max_age IS NULL
     OR b.sprt_trgt_max_age >= TIMESTAMPDIFF(YEAR, #{birthDate}, CURDATE()))
```

`TIMESTAMPDIFF(YEAR, 생년월일, 오늘)`이 만 나이다.

**`IS NULL OR`이 붙은 이유** — 나이 제한이 없는 정책은 값이 NULL이다. SQL에서 NULL과 비교하면 결과가 참도 거짓도 아닌 '알 수 없음'이 되어 조건에서 탈락한다. **제한이 없는데 오히려 걸러지는** 일이 생기므로 명시적으로 통과시킨다.

**주의** — 회원의 생년월일이 비어 있으면 만 나이가 NULL이 되고, 나이 제한이 있는 정책이 **전부** 탈락한다.

---

## 소득

```sql
AND (
  b.earn_cnd_se_cd = '0043001'
  OR b.earn_cnd_se_cd = '0043003'
  OR (
    b.earn_cnd_se_cd = '0043002'
    AND (b.earn_min_amt IS NULL OR (#{income} * 12 / 10000) >= b.earn_min_amt)
    AND (b.earn_max_amt IS NULL OR b.earn_max_amt >= (#{income} * 12 / 10000))
  )
)
```

소득 조건이 세 종류다.

| 코드 | 의미 | 처리 |
| --- | --- | --- |
| `0043001` | 제한없음 | 그냥 통과 |
| `0043003` | 기타 (자연어) | 그냥 통과 + 화면에 "확인 필요" |
| `0043002` | 연소득 기준 | 숫자로 비교 |

**`#{income} * 12 / 10000`이 하는 일**

회원 프로필의 소득은 **월 단위 원**이고, 정책의 기준은 **연 단위 만원**이다. 단위가 달라서 맞춰준다.

```
2,074,000원(월) × 12 = 24,888,000원(연) ÷ 10000 = 2,488만원
```

**`0043003`을 왜 그냥 통과시키나?**

이 값들은 "중위소득 150% 이하", "본인 연소득 6천만원 이하" 같은 **문장으로만** 들어온다. SQL로 판정할 수 없다. 걸러내면 자격이 되는 사람도 놓치므로, 통과시키고 원문을 화면에 보여준다. 대신 점수를 낮게 준다.

약 328건이 여기 해당한다. 실제로 소득이 필터 역할을 하는 건 `0043002` 30건 정도, 전체의 1%다.

---

## 혼인

```sql
AND (b.mrg_stts_cd IS NULL
     OR b.mrg_stts_cd = '0055003'
     OR b.mrg_stts_cd = #{mrgSttsCd})
```

`0055003`이 '제한없음' 코드다. 대부분의 정책이 이 값이라 사실상 거의 다 통과한다.

---

## 취업·학력·전공 — 매핑 테이블 방식

한 정책이 여러 취업상태를 허용할 수 있어서 별도 테이블로 뺐다. 세 축의 구조가 같다.

```sql
AND (
  NOT EXISTS (SELECT 1 FROM benefit_job bj WHERE bj.benefit_no = b.benefit_no)
  OR EXISTS (SELECT 1 FROM benefit_job bj
             WHERE bj.benefit_no = b.benefit_no AND bj.job_cd = #{employStatus})
  OR EXISTS (SELECT 1 FROM benefit_job bj
             WHERE bj.benefit_no = b.benefit_no AND bj.job_cd = '0013010')
)
```

세 갈래 중 하나만 맞으면 통과다.

| 조건 | 뜻 |
| --- | --- |
| `NOT EXISTS` | 매핑 행이 아예 없음 = **제한이 없는 정책**이라 통과 |
| `job_cd = #{employStatus}` | 내 취업상태가 목록에 있음 |
| `job_cd = '0013010'` | 정책이 '제한없음'을 명시함 |

학력은 `benefit_school`·`0049010`, 전공은 `benefit_major`·`0011009`로 같은 구조다.

---

## 지역 — 계층을 두 단계까지 본다

```sql
AND (
  NOT EXISTS (SELECT 1 FROM benefit_region br WHERE br.benefit_no = b.benefit_no)
  OR EXISTS (
    SELECT 1
    FROM benefit_region br
    LEFT JOIN region pr  ON pr.zip_cd  = br.zip_cd
    LEFT JOIN region pr2 ON pr2.zip_cd = pr.parent_region_code
    LEFT JOIN region mr  ON mr.zip_cd  = #{regionCode}
    LEFT JOIN region mr2 ON mr2.zip_cd = mr.parent_region_code
    WHERE br.benefit_no = b.benefit_no
    AND (
      br.zip_cd = #{regionCode}
      OR pr.parent_region_code  = #{regionCode}
      OR pr2.parent_region_code = #{regionCode}
      OR br.zip_cd = mr.parent_region_code
      OR br.zip_cd = mr2.parent_region_code
    )
  )
)
```

여기가 제일 복잡하다. **온통청년이 정책 지역을 항상 최하위 행정구역으로 펼쳐서 주기 때문**이다.

```
전국 정책      → 시군구 코드 200개 넘게 나열
경기도 정책    → 41111, 41113, 41115 ... 경기도 모든 시군구
수원시 정책    → 41111, 41113, 41115, 41117 (장안·권선·팔달·영통)
```

`benefit_region`에는 **최하위 코드만** 들어 있다. `41000 경기도` 같은 광역 코드로 저장된 행은 하나도 없다.

그런데 회원이 고른 지역 단위는 사람마다 다르다.

| 회원 지역 | 정책까지의 거리 |
| --- | --- |
| 41111 장안구 | 0단계 — 정확히 일치 |
| 41110 수원시 | 1단계 아래 |
| 41000 경기도 | 2단계 아래 |

**한 단계만 비교하면 광역시도를 고른 회원이 지역 정책을 하나도 못 받는다.**

그래서 조인을 네 개 건다.

| 별칭 | 무엇 |
| --- | --- |
| `pr` | 정책 지역의 부모 |
| `pr2` | 정책 지역의 조부모 |
| `mr` | 회원 지역의 부모 |
| `mr2` | 회원 지역의 조부모 |

그리고 다섯 가지 경우를 본다.

```
br.zip_cd  = 회원지역          정확히 일치
pr.parent  = 회원지역          정책이 한 단계 아래
pr2.parent = 회원지역          정책이 두 단계 아래
br.zip_cd  = mr.parent         정책이 한 단계 위
br.zip_cd  = mr2.parent        정책이 두 단계 위
```

**실측** — 41000 경기도 매칭 0건 → 525건, 41110 수원시 0건 → 458건.

`NOT EXISTS`가 앞에 있는 이유는 앞의 세 축과 같다. 지역 매핑이 아예 없는 정책은 지역 제한이 없는 것으로 보고 통과시킨다.

---

# 2단계 · 중복수혜 검사

## 왜 필요한가

청년 정책은 **아무거나 골라서 다 받을 수 있는 게 아니다.**

> 햇살론유스를 받고 있으면 미소금융 청년 대출은 못 받는다.
> 둘 다 청년 대상 서민금융 소액대출이라 한도 심사가 연계되기 때문이다.

같은 목적의 지원을 중앙정부와 지자체가 따로 운영하는 경우가 많다. 제도는 미리 "둘 중 하나만"이라고 정해두는데, **청년 본인은 그걸 모른다.** 신청하고 나서야 반려되거나 환수당한다.

**그리고 온통청년 API는 이 관계를 알려주지 않는다.** 정책 하나하나의 내용만 줄 뿐이다. 그래서 관리자가 공고문을 읽고 직접 등록하는 구조로 만들었다.

---

## 관계를 저장하는 두 가지 방법

### 방법 A · 그룹형 — "이 묶음에서 하나만"

같은 성격의 정책에 **같은 이름표**를 붙인다.

```
G01 · 서민금융 청년 소액대출
   ├ 햇살론유스
   └ 미소금융 청년 미래이음 대출

G02 · 평택시 청년창업 자금 지원
   ├ 우수초기창업자 지원사업
   ├ 청년창업 금융지원 이차보전
   └ 청년창업자 금융지원사업

G03 · 의성군 청년 주거공간
   ├ 금강장
   └ 금수장

G04 · 청년 전월세 중개보수 감면
   ├ 평택시 중개보수료 감면
   └ 용인청년 부동산 중개보수 감면
```

**세 개 이상이 서로 겹칠 때 편하다.** G02처럼 셋이 서로서로 겹치면 쌍으로 적을 때 3줄이 필요한데, 이름표는 3개만 붙이면 끝난다.

> 저장 위치: `benefit.conflict_group_code`

### 방법 B · 개별쌍 — "A와 B는 같이 못 받는다"

성격이 다른데도 규정상 겹치는 경우다. 둘씩 짝지어 적는다.

```
햇살론유스  ↔  청년주택드림청약통장
   "햇살론유스 상환 중에는 청약통장 연계 대출의
    우대 조건이 일부 제한될 수 있습니다"
```

대출과 청약통장은 성격이 달라 묶음으로 만들 수 없다. 다른 대출과는 또 관계가 없다.

> 저장 위치: `benefit_conflict_rule`에 한 줄씩
> 순서 없는 관계이므로 **작은 번호를 trigger, 큰 번호를 target**에 넣는다. DB의 CHECK 제약이 강제한다.

---

## 개별쌍에는 세기가 있다

| 유형 | 의미 | 후보에서 | 경고 | 점수 |
| --- | --- | --- | --- | --- |
| **중복불가** | 규정상 아예 안 됨 | **제거** | — | — |
| **일부제한** | 받을 수는 있는데 조건이 나빠짐 | 유지 | 표시 | **20점 미부여** |
| **확인필요** | 상황에 따라 다름 | 유지 | 표시 | **20점 미부여** |
| **외부 제도** | 우리 목록 밖 제도와 겹침 | 유지 | 표시 | 영향 없음 |

**중복불가만 지운다.** 받을 수 있는 걸 함부로 지우면 사용자가 기회를 잃는다. 나머지는 알려주고 **판단은 사용자에게 맡긴다.**

점수는 유형이 아니라 **"내부 경고가 있느냐"**로 갈린다. 일부제한이든 확인필요든 경고가 붙으면 20점을 못 받는다.

**외부 제도**는 실업급여·국민취업지원제도처럼 우리가 추천하지 않는 제도다. `trigger_benefit_no`를 NULL로 두고 등록한다. 비어 있으면 "외부"라는 뜻이다. **사용자가 실제로 받는지 알 수 없어** 안내만 하고 점수에는 반영하지 않는다.

---

## 두 번 걸러진다 ← 여기가 핵심

```
① 내가 이미 받는 것  vs  후보     →  정책을 지운다
② 후보끼리                        →  조합을 지운다. 정책은 살아있다
```

### 첫 번째 — 보유 vs 후보

회원 2번은 이 둘을 이미 받고 있다.

```
햇살론유스 (G01)
평택시 우수초기창업자 지원사업 (G02)
```

**이미 받는 것 제외**

```sql
AND b.benefit_no NOT IN (
  SELECT benefit_no FROM applied_benefit WHERE member_no = #{memberNo}
)
```

같은 걸 또 추천하지 않는다.

**그룹형 검사**

```sql
AND NOT EXISTS (
  SELECT 1
  FROM applied_benefit ab
  JOIN benefit ob ON ob.benefit_no = ab.benefit_no
  WHERE ab.member_no = #{memberNo}
    AND ob.conflict_group_code IS NOT NULL
    AND b.conflict_group_code IS NOT NULL
    AND ob.conflict_group_code = b.conflict_group_code
)
```

"내가 받는 정책 중에 이 후보와 같은 이름표를 단 게 있으면 빼라"는 뜻이다.

```
미소금융 청년 미래이음 대출     G01 → 햇살론유스와 같은 묶음 → 제거
평택시 청년창업 이차보전        G02 → 우수초기창업자와 같은 묶음 → 제거
평택시 청년창업자 금융지원      G02 → 같은 묶음 → 제거
```

**개별쌍 중복불가 검사**

```sql
AND NOT EXISTS (
  SELECT 1
  FROM benefit_conflict_rule r
  JOIN applied_benefit ab ON ab.member_no = #{memberNo}
  WHERE r.confirm_status = '확정'
    AND r.is_active = 'Y'
    AND r.conflict_type = '중복불가'
    AND r.trigger_benefit_no IS NOT NULL
    AND ((r.trigger_benefit_no = ab.benefit_no AND r.target_benefit_no = b.benefit_no)
      OR (r.target_benefit_no  = ab.benefit_no AND r.trigger_benefit_no = b.benefit_no))
)
```

마지막 두 줄이 **양방향 검사**다. 작은 번호를 trigger에 넣기로 했으므로, 내가 받는 정책이 trigger일 수도 target일 수도 있다. 양쪽 다 봐야 한다.

`trigger_benefit_no IS NOT NULL` 조건은 **외부 제도 규칙을 제외**하는 것이다. 외부 제도는 지우지 않는다.

```
평택시 크라우드 펀딩 지원
  ↔ 우수초기창업자와 중복불가 → 제거
```

여기까지가 **후보 39건**이다.

### 두 번째 — 조합 내부

후보 안에서 정책을 조합할 때 또 검사한다. 이건 SQL이 아니라 자바에서 한다.

**왜 또 하나?** 첫 번째는 "내가 받는 것 vs 후보"만 봤다. **후보끼리 겹치는 건 아직 안 봤다.**

```
청년미래플러스        ← 후보에 있음
안성시 청년내일캠프    ← 후보에도 있음
        ↕
  둘이 중복불가 관계 (둘 다 취업 컨설팅 제공)
```

**각각 따로는 받을 수 있다.** 그래서 후보에서 지우지 않고 **같은 조합에만 못 들어가게** 한다.

그룹형도 같다. 금강장과 금수장은 둘 다 후보에 남지만 한 조합에 같이 들어가지 않는다.

### 왜 미리 지우면 안 되는가

점수 높은 것 하나만 미리 남기면 **더 좋은 조합을 놓친다.**

```
A1(100점, X와 충돌)   A2(90점)   X(90점)   Y(80점)
※ A1과 A2는 같은 그룹

대표만 남기면      A1 + Y      = 180점   ← X는 A1과 충돌해 못 들어감
조합에서 판단하면   A2 + X + Y  = 260점
```

미리 지웠으면 260점 조합을 영영 못 찾는다.

---

## 검수 상태 — 승인된 것만 적용된다

위 쿼리에 이 두 줄이 항상 붙어 있다.

```sql
AND r.confirm_status = '확정'
AND r.is_active = 'Y'
```

| 상태 | 엔진이 |
| --- | --- |
| 확정 · 활성 | 적용함 |
| 검수필요 | **무시함** |
| 비활성 | **무시함** |

현재 등록된 11건 중 **7건만 적용된다.**

```
확정 · 활성      7건
검수필요          3건   ← 대기 중
비활성            1건   ← 폐기된 규칙
```

규칙은 관리자가 공고문을 읽고 판단해 넣는 것이다. **잘못 넣으면 사용자가 받을 수 있는 정책을 못 받게 된다.** 그래서 검수를 거친 것만 적용한다. 폐기된 규칙도 지우지 않고 비활성으로 남겨 판단 근거를 되짚을 수 있게 한다.

## 규칙은 어떤 기준으로 정했나

정책의 지원 내용과 주관기관을 직접 읽고, 아래 중 하나에 해당하는 쌍만 등록했다.

| 기준 | 예시 |
| --- | --- |
| 같은 기관·같은 목적의 자금 지원 | 평택시 창업 자금 3종 (G02) |
| 물리적으로 동시 이용 불가 | 의성군 주거공간 2곳 (G03) |
| 지원 항목이 실질적으로 겹침 | 취업 컨설팅 + 직무교육 |

**각 기관이 실제로 고시한 규정임을 확인한 것은 아니다.** 그래서 일부는 검수필요로 남겨뒀다. 실제 규정처럼 인용하면 안 된다.

---

# 3단계 · 점수 계산

정책 하나당 100점 만점. 배점은 `EngineServiceImpl`에 상수로 있다.

| 항목 | 배점 | 조건 |
| --- | --- | --- |
| 인기도 | 10 / 7 / 4 / 1 | 조회수 1000 이상 / 300 / 100 / 그 미만 |
| 소득 조건 | 40 | `0043001` 또는 `0043002` 통과 |
| 소득 조건 | 10 | `0043003` (판정 불가) |
| 마감 임박도 | 30 / 20 / 15 / 10 | D-30 / D-90 / 상시모집 / 그 외 |
| 중복수혜 | 20 | 내부 경고가 하나도 없을 때 |

**금액이 아니라 인기도·자격 확실성·시급성으로 점수를 낸다.** `support_amount` 컬럼은 있지만 온통청년이 값을 주지 않아 전부 NULL이기 때문이다.

점수 상위 **20개**만 조합에 쓴다. 20개면 조합 후보가 1,350개 나오고, 그 이상은 계산량이 급증한다.

**정렬**: 점수 높은 순 → 마감일 가까운 순 → 정책번호 오름차순

---

# 4단계 · 조합 생성과 평가

상위 20개로 1개·2개·3개짜리 조합을 전부 만들고, 충돌하는 조합을 버린 뒤 하나를 고른다.

```
combinationScore = 평균 점수
                 + (정책 수 − 1) × 3
                 + (서로 다른 카테고리 수 − 1) × 4
```

단순 합계면 3개짜리가 무조건 이기고, 단순 평균이면 1개짜리가 유리해져서 절충했다.

**⚠️ 이 점수는 100점 만점이 아니다.** 보너스가 붙어 100을 넘을 수 있다. 화면에 `102점/100점`이나 프로그레스 바로 표시하면 안 된다.

**조합 정렬 7단계**

| 순위 | 기준 | 이유 |
| --- | --- | --- |
| 1 | 정책 수 많은 순 | 3개 조합을 항상 우선 |
| 2 | 조합 점수 | |
| 3 | 내부 경고가 적은 순 | |
| 4 | 조합 내 최저 정책점수 | 약한 정책이 평균에 가려지는 것 방지 |
| 5 | 가장 빠른 마감일 (상시는 뒤로) | 같은 구간 안에서 시급성 구분 |
| 6 | Σ log(1 + 조회수) | 인기 정책 하나가 조합을 지배하는 것 완화 |
| 7 | 정렬된 정책번호 목록 | **결정론 보장용. 화면 노출 금지** |

7번은 같은 입력이면 항상 같은 결과가 나오게 하는 기술적 기준이다. "추천 이유"로 보여주면 안 된다.

정렬 1순위가 정책 수이므로 **3개짜리가 하나라도 있으면 항상 3개짜리가 선택된다.** 따라서 "전체 조합 중 최적"이 아니라 **"정책 3개 조합 중 최적"**이라고 표현해야 정확하다.

---

# 5. 응답 필드

```
GET /api/engine/benefits
```

**회원번호를 파라미터로 받지 않는다.** 서버가 JWT 토큰에서 꺼낸다. 주소창에서 번호만 바꿔 남의 결과를 보는 것을 막기 위해서다.

| 필드 | 설명 |
| --- | --- |
| `benefits` | 자격조건을 통과한 후보 전체 |
| `topBenefits` | 점수 상위 20건 |
| `recommendedCombinations` | 추천 조합. 정책 3개짜리 1개 |
| `warnings` | 내부 정책 간 경고 (일부제한·확인필요) |
| `externalWarnings` | 외부 제도 경고 |

각 정책에 `score`(0~100), `scoreDetail`(근거 문장 10개), `aplyUrlAddr`(신청 URL)이 붙는다.

**신청 URL은 그대로 쓰면 안 된다.** 온통청년 데이터가 정제되어 있지 않다.

- 대부분 빈 문자열이며 NULL이 아니다
- `https://` 없이 `www.`로 시작하는 값이 많다
- `-`, `전화문의` 같은 URL이 아닌 값이 있다
- `&`가 `&amp;`로 저장돼 파라미터가 깨진다

---

# 6. 알려진 한계

| 항목 | 내용 |
| --- | --- |
| 전역 최적이 아님 | 상위 20개로 추린 뒤 조합한다. **"전역 최적"이라고 표현하면 안 된다.** "선별된 후보군 안에서의 최적"이 정확하다 |
| 프로필 미입력 처리 | **미구현.** 프로필이 아예 없으면 현재는 500 오류가 난다 |
| 매칭 이유 표시 | `scoreDetail`이 '연령 조건 충족'처럼 충족 여부만 알려주고 실제 값은 안 보인다 |
| 지원 금액 미반영 | `support_amount`가 전부 NULL |
| 기타 소득조건 | 약 328건이 자연어라 판정 불가. 자격 미달 정책이 섞일 수 있다 |
| 지역 데이터 품질 | 주관기관이 특정 지자체인데 전국 지역코드가 부여된 정책이 있다 |
| 중위소득 기준 | `household_size`를 아직 쓰지 않는다 |
| 외부 제도 경고 | 사용자가 실제로 받는지 모르므로 모든 사용자에게 동일하게 표시된다 |
| 중복수혜 규칙 데이터 | 온통청년이 제공하지 않아 관리자가 직접 등록한다. 현재 데이터는 시연용이다 |
| 관리자 화면 | 혜택 목록의 중복규칙 칸이 그룹형만 보여준다. 개별쌍은 확인할 방법이 없다 |
| 테스트 코드 | 없음 |

**개선 방향** — 자연어 소득조건은 LLM으로 미리 구조화해 DB에 저장하고, 판정 자체는 기준중위소득 표를 이용한 계산으로 처리한다. 판정에 LLM을 쓰지 않는 이유는 같은 사용자에게 항상 같은 결과가 나와야 하기 때문이다.

---

# 7. 직접 확인하는 법

## 세팅 순서

```
1. DB 새로 만들기
2. schema.sql → data_0_code.sql → data_1_base.sql
3. 백엔드 실행 (Redis 켜고, OpenAI 키 환경변수 등록)
4. admin 로그인 → 관리자 대시보드 → 동기화 실행     ← 반드시 먼저
5. db/engine_test_data.sql (v2.2) 실행             ← 중복수혜 규칙
6. user02 로그인 → /engine
```

`data_2_benefit_real.sql`은 실행하지 않는다. 혜택은 4번 동기화가 채운다.

**4번을 건너뛰고 5번을 하면 오류가 난다.** 혜택이 없는데 혜택 번호를 찾으려 하기 때문이다.

`engine_test_data.sql`은 **전체 선택(Ctrl+A) 후 한 번에 실행**해야 한다. 파일 위쪽에서 정책 번호를 찾아 변수에 담고 아래쪽에서 쓰는데, 부분만 실행하면 변수가 사라진다.

## 확인 쿼리

### ① 혜택이 들어왔나

```sql
SELECT COUNT(*) FROM benefit;
```

2,600건 이상이면 정상. **0이면 동기화를 안 한 것이다.**

### ② 그룹 이름표가 붙었나

```sql
SELECT conflict_group_code, COUNT(*) AS cnt
FROM benefit
WHERE conflict_group_code IS NOT NULL
GROUP BY conflict_group_code;
```

`G01 2 / G02 3 / G03 2 / G04 2`가 나와야 한다.

### ③ 개별쌍 규칙이 들어갔나

```sql
SELECT confirm_status, is_active, COUNT(*) AS cnt
FROM benefit_conflict_rule
GROUP BY confirm_status, is_active;
```

`확정·Y 7 / 검수필요·Y 3 / 확정·N 1`이 나와야 한다.
**0이면 엔진 화면에 경고가 하나도 안 뜬다.**

### ④ 규칙 내용 보기

```sql
SELECT r.rule_no,
       r.conflict_type   AS 유형,
       r.confirm_status  AS 검수,
       r.is_active       AS 활성,
       IFNULL(t.plcy_nm, '(외부 제도)') AS 기준정책,
       g.plcy_nm         AS 대상정책,
       r.rule_text       AS 내용
FROM benefit_conflict_rule r
LEFT JOIN benefit t ON t.benefit_no = r.trigger_benefit_no
JOIN benefit g ON g.benefit_no = r.target_benefit_no
ORDER BY r.rule_no;
```

11줄이 나온다.

`t`가 기준정책, `g`가 대상정책이다. **`t`를 `LEFT JOIN`으로 건 이유**는 외부 제도 규칙에서 `trigger_benefit_no`가 NULL이기 때문이다. 일반 `JOIN`으로 걸면 그 두 줄이 사라진다.
`기준정책`이 `(외부 제도)`로 나오면 실업급여 같은 우리 DB 밖의 제도와 겹치는 경우다.

### ⑤ 보유 정책이 있나

```sql
SELECT COUNT(*) FROM applied_benefit WHERE member_no = 2;
```

2가 나와야 한다. **이게 0이면 그룹형 검사가 아무것도 안 거른다. 비교할 대상이 없기 때문이다.**

### ⑥ 지역 매칭이 되나

```sql
SET @me = '41000';

SELECT COUNT(DISTINCT br.benefit_no) AS 지역매칭
FROM benefit_region br
LEFT JOIN region pr  ON pr.zip_cd  = br.zip_cd
LEFT JOIN region pr2 ON pr2.zip_cd = pr.parent_region_code
LEFT JOIN region mr  ON mr.zip_cd  = @me
LEFT JOIN region mr2 ON mr2.zip_cd = mr.parent_region_code
WHERE br.zip_cd = @me
   OR pr.parent_region_code  = @me
   OR pr2.parent_region_code = @me
   OR br.zip_cd = mr.parent_region_code
   OR br.zip_cd = mr2.parent_region_code;
```

엔진의 지역 조건만 떼어낸 쿼리다. `@me`를 `41000`(경기도) → `41110`(수원시) → `41111`(장안구)로 바꿔가며 돌려보면 된다.

**셋 다 0이 아니면 두 단계 확장이 적용된 것이다.** 확장 전에는 41000과 41110이 0이었다.

---

**⚠️ MyBatis XML이나 Java를 수정했으면 서버를 Stop → Run으로 완전히 재시작해야 한다.** Redeploy나 Update classes로는 반영되지 않는다. DB 데이터만 바꿨으면 재시작이 필요 없다.