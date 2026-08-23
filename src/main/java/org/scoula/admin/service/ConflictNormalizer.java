package org.scoula.admin.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;

/**
 * AI 출력 후처리.
 *
 * 프롬프트로 두 번 금지했는데도 targetName 에 "유사사업" 같은 범주 표현이
 * 계속 들어온다. 프롬프트를 더 조이는 대신 여기서 결정론적으로 건다.
 * 최종 판단은 Java gate 가 한다는 설계 원칙에도 맞다.
 */
public final class ConflictNormalizer {

    /** 이 표현만으로 이루어지면 고유 정책명이 아니다 */
    private static final List<String> CATEGORY_TAILS = Arrays.asList(
            "유사사업", "유사 사업", "동일유사사업", "동일·유사사업", "동일유사 사업",
            "중복사업", "타사업", "타 사업", "타제도", "타 제도",
            "지원사업", "유사지원사업", "유사 지원사업", "중복참여 불가 사업",
            "일자리사업", "직접일자리사업", "재정지원 일자리사업", "재정일자리사업",
            "창업지원사업", "주거지원 사업", "주거관련 유사 사업",
            "자산형성사업", "유사자산형성사업", "유사 자산형성 지원사업",
            "인턴 사업", "장학사업", "유사한 성격의 사업", "유사한 지원"
    );

    /** 정책명이 아니라 사람의 상태나 기관 */
    private static final List<String> NOT_POLICY = Arrays.asList(
            "국가", "타 지자체", "타지자체", "기업체", "공공기관", "정부",
            "동아리", "수당", "인건비", "이사비", "4대 보험 가입자",
            "사업자 등록자", "병역미필자", "상근직원"
    );

    private ConflictNormalizer() {}

    /**
     * targetName 이 실제 고유 정책명인지.
     * 아니면 범주로 취급해 targetCategory 로 옮긴다.
     */
    public static boolean isRealPolicyName(String name) {
        if (name == null) return false;
        String t = name.trim();
        if (t.length() < 3) return false;

        String c = compact(t);
        for (String bad : NOT_POLICY) {
            if (c.equals(compact(bad))) return false;
        }
        for (String tail : CATEGORY_TAILS) {
            if (c.equals(compact(tail))) return false;
        }
        // "타 OO사업" 처럼 이 사업 밖을 가리키는 수식어로 시작하면 범주다
        if (t.startsWith("타 ") || t.startsWith("타지") || t.startsWith("다른 ")
                || t.startsWith("유사") || t.startsWith("동일")) {
            return false;
        }
        return true;
    }

    /**
     * evidence 안에 이름이 실제로 있는지.
     * 한국어는 띄어쓰기와 괄호가 제각각이라 raw 완전일치는 너무 엄격하다.
     * 반대로 유사도로 통과시키면 지어낸 이름을 못 거른다. 그래서 정규화 후 substring 만 본다.
     */
    public static boolean evidenceContains(String evidence, String name) {
        if (isBlank(evidence) || isBlank(name)) return false;
        return compact(evidence).contains(compact(name));
    }

    public static String compact(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFKC);
        t = t.replaceAll("[\\s\u00A0]", "");
        t = t.replaceAll("[()\\[\\]{}「」『』｢｣\"'‘’“”·・、,.\\-–—~/*]", "");
        return t.toLowerCase();
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * dedupe_key 생성. 같은 의미가 두 번 저장되지 않게 한다.
     *
     * 여기 들어가는 것은 "이 공고문이 누구에 대해 어떤 관계를 말했는가" 뿐이다.
     *
     * timing·subject·restrictionStage·direction 은 넣지 않는다.
     * 같은 본문을 다시 분석해도 값이 흔들릴 수 있기 때문이다.
     * 실제로 같은 정책·같은 본문·같은 프롬프트인데 timing 만 달라
     * 두 행으로 저장된 사례가 여러 정책에서 나왔다.
     * 흔들리는 값을 키에 넣으면 같은 관계가 실행할 때마다 쌓인다.
     *
     * mappedBenefitNo 도 넣지 않는다.
     * 그 값은 Resolver 가 나중에 채우거나 바꾸는 것이라,
     * 키에 넣으면 매칭 상태가 변할 때 같은 추출 사실의 정체성이 바뀐다.
     *
     * 대신 상대 이름을 넣는다.
     * 예전에는 이름이 키에 없어서 한 공고가 지목한 서로 다른 외부 제도 둘이
     * 같은 키가 되어 하나가 조용히 사라질 수 있었다.
     *
     * 개별쌍을 min/max 로 묶는 것은 Rule 쪽 표현이다.
     * A 공고가 B 를 말한 것과 B 공고가 A 를 말한 것은
     * 서로 다른 공식 근거이므로 Candidate 에서는 합치지 않는다.
     */
    public static String buildDedupeKey(Integer sourceNo, String relation,
                                        String targetName, String category) {

        String rel = isBlank(relation) ? "UNKNOWN" : relation.trim();

        if (!isBlank(targetName)) {
            return "NAME:" + sourceNo + ":" + rel + ":" + compact(targetName);
        }
        return "CAT:" + sourceNo + ":" + rel + ":"
                + (isBlank(category) ? "NONE" : compact(category));
    }

    /**
     * 상대 지목을 이름과 범주로 가른 결과.
     *
     * AI 가 targetName 에 범주 표현을 계속 넣어서 저장 단계에서 옮겨 담는데,
     * 그 계산이 후보 저장과 관측 기록 두 곳에서 필요하다.
     * 두 곳이 각자 계산하면 한쪽만 고쳤을 때 조용히 어긋나고,
     * 그러면 같은 AI 출력이 서로 다른 키를 갖게 된다.
     */
    public record TargetIdentity(String name, String category) {}

    /**
     * targetName 이 고유 정책명이 아니면 범주 쪽으로 옮긴다.
     *
     * 옮길 범주가 이미 있으면 그것을 쓰고, 없으면 이름을 그대로 범주로 삼는다.
     * 이름이 범주였다는 사실 자체가 정보이므로 버리지 않는다.
     *
     * 이 메서드는 기존 저장 로직에서 그대로 뽑아낸 것이다.
     * 값이 달라지면 안 되므로 뽑아내기 전후를 비교하는 테스트가 함께 있다.
     */
    public static TargetIdentity normalizeTarget(String targetName, String targetCategory) {
        String name = targetName;
        String category = targetCategory;

        if (!isRealPolicyName(name)) {
            if (isBlank(category)) category = name;
            name = null;
        }
        return new TargetIdentity(name, isBlank(category) ? null : category);
    }
}
