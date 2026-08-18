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

    /** dedupe_key 생성. 같은 의미가 두 번 저장되지 않게 한다 */
    public static String buildDedupeKey(Integer sourceNo, Integer mappedNo,
                                        String direction, String category,
                                        String timing, String subject) {
        if (mappedNo != null) {
            if ("BIDIRECTIONAL".equals(direction)) {
                int a = Math.min(sourceNo, mappedNo);
                int b = Math.max(sourceNo, mappedNo);
                return "PAIR:BIDIR:" + a + ":" + b;
            }
            return "PAIR:DIR:" + sourceNo + ":" + mappedNo;
        }
        return "WARN:" + sourceNo + ":"
                + (isBlank(category) ? "NONE" : compact(category)) + ":"
                + (isBlank(timing) ? "UNKNOWN" : timing) + ":"
                + (isBlank(subject) ? "UNKNOWN" : subject);
    }
}