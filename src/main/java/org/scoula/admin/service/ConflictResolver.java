package org.scoula.admin.service;

import lombok.Data;
import org.scoula.admin.dto.BenefitNameDto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 가 뽑은 정책명을 우리 DB 의 benefit_no 로 연결한다.
 *
 * 순서는 정규화 완전일치 → 포함관계 후보 검색이다.
 * 임베딩이나 편집거리로 최종 확정하지 않는다.
 * 동명이지만 지역이 다른 정책을 잘못 연결하면
 * 그 지역 사용자만 받을 수 있었던 정책을 잃는다.
 */
public class ConflictResolver {

    /** 너무 짧으면 포함관계 검색이 아무거나 다 걸린다 */
    private static final int MIN_CONTAINS_LENGTH = 6;

    /** 후보가 이보다 많으면 이름이 너무 일반적이라는 뜻이므로 매칭을 포기한다 */
    private static final int MAX_CANDIDATES = 8;

    @Data
    public static class Result {
        private String  resolverResult;   // UNIQUE_MATCH / MULTI_MATCH / NO_MATCH
        private Integer mappedBenefitNo;
        private String  candidates;       // MULTI 일 때 benefit_no 목록
    }

    private final List<BenefitNameDto> dictionary;
    private final List<String> compactNames;

    public ConflictResolver(List<BenefitNameDto> dictionary) {
        this.dictionary = dictionary;
        this.compactNames = dictionary.stream()
                .map(d -> ConflictNormalizer.compact(d.getPlcyNm()))
                .collect(Collectors.toList());
    }

    public Result resolve(String targetName, Integer sourceBenefitNo) {

        Result r = new Result();

        if (!ConflictNormalizer.isRealPolicyName(targetName)) {
            r.setResolverResult("NO_MATCH");
            return r;
        }

        String key = ConflictNormalizer.compact(targetName);
        if (key.isEmpty()) {
            r.setResolverResult("NO_MATCH");
            return r;
        }

        // 1단계 - 정규화 완전일치
        List<BenefitNameDto> exact = new ArrayList<>();
        for (int i = 0; i < dictionary.size(); i++) {
            BenefitNameDto d = dictionary.get(i);
            if (d.getBenefitNo().equals(sourceBenefitNo)) continue;   // 자기 자신 제외
            if (compactNames.get(i).equals(key)) exact.add(d);
        }
        if (!exact.isEmpty()) return decide(r, exact);

        // 2단계 - 포함관계. 짧은 이름은 건너뛴다
        if (key.length() < MIN_CONTAINS_LENGTH) {
            r.setResolverResult("NO_MATCH");
            return r;
        }

        List<BenefitNameDto> partial = new ArrayList<>();
        for (int i = 0; i < dictionary.size(); i++) {
            BenefitNameDto d = dictionary.get(i);
            if (d.getBenefitNo().equals(sourceBenefitNo)) continue;
            String name = compactNames.get(i);
            if (name.isEmpty()) continue;
            if (name.contains(key) || key.contains(name)) partial.add(d);
        }

        if (partial.isEmpty() || partial.size() > MAX_CANDIDATES) {
            r.setResolverResult("NO_MATCH");
            return r;
        }
        return decide(r, partial);
    }

    private Result decide(Result r, List<BenefitNameDto> found) {
        if (found.size() == 1) {
            r.setResolverResult("UNIQUE_MATCH");
            r.setMappedBenefitNo(found.get(0).getBenefitNo());
            return r;
        }
        r.setResolverResult("MULTI_MATCH");
        r.setCandidates(found.stream()
                .map(d -> String.valueOf(d.getBenefitNo()))
                .limit(MAX_CANDIDATES)
                .collect(Collectors.joining(",")));
        return r;
    }
}
