package org.scoula.benefit.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.benefit.dto.*;
import org.scoula.benefit.service.BenefitService;
import org.scoula.member.mapper.MemberMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.security.Principal;
import java.util.Collections;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Collections;

import static io.jsonwebtoken.Jwts.header;

@RestController
@RequestMapping("/api/benefit")
@RequiredArgsConstructor
public class BenefitController {

    private final BenefitService benefitService;
    private final MemberMapper memberMapper;

    @GetMapping(
            value = "/external/youth-center/raw",
            produces = "application/json; charset=UTF-8"
    )
    public ResponseEntity<String> getYouthCenterRaw(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(defaultValue = "json") String rtnType,
            @RequestParam(required = false) String plcyNm,
            @RequestParam(required = false) String plcyKywdNm,
            @RequestParam(required = false) String lclsfNm,
            @RequestParam(required = false) String mclsfNm,
            @RequestParam(required = false) String zipCd
    ) {
        YouthPolicyRequestDTO requestDTO = new YouthPolicyRequestDTO();
        requestDTO.setPageNum(pageNum);
        requestDTO.setPageSize(pageSize);
        requestDTO.setRtnType(rtnType);
        requestDTO.setPlcyNm(plcyNm);
        requestDTO.setPlcyKywdNm(plcyKywdNm);
        requestDTO.setLclsfNm(lclsfNm);
        requestDTO.setMclsfNm(mclsfNm);
        requestDTO.setZipCd(zipCd);

        String response = benefitService.getYouthPolicyRaw(requestDTO);

        return ResponseEntity.ok()
                .header("Content-Type", "application/json; charset=UTF-8")
                .body(response);
    }

    @PostMapping("/sync/youth-center")
    public ResponseEntity<Map<String, Object>> syncYouthCenterPolicies(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(defaultValue = "json") String rtnType,
            @RequestParam(required = false) String plcyNm,
            @RequestParam(required = false) String lclsfNm,
            @RequestParam(required = false) String mclsfNm,
            @RequestParam(required = false) String zipCd
    ) {
        YouthPolicyRequestDTO requestDTO = new YouthPolicyRequestDTO();
        requestDTO.setPageNum(pageNum);
        requestDTO.setPageSize(pageSize);
        requestDTO.setRtnType(rtnType);
        requestDTO.setPlcyNm(plcyNm);
        requestDTO.setLclsfNm(lclsfNm);
        requestDTO.setMclsfNm(mclsfNm);
        requestDTO.setZipCd(zipCd);

        int savedCount = benefitService.syncYouthPolicies(requestDTO);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "온통청년 정책 데이터 동기화 완료");
        result.put("savedCount", savedCount);

        return ResponseEntity.ok(result);

    }

    //관리자 기간별 동기화 API
    @PostMapping("/admin/sync/youth-center/frst-reg-date")
    public ResponseEntity<Integer> syncYouthPoliciesByFrstRegDt(
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        int count = benefitService.syncYouthPoliciesByFrstRegDt(startDate, endDate);
        return ResponseEntity.ok(count);
    }

    @GetMapping
    public ResponseEntity<List<BenefitListResDTO>> getBenefit(
            BenefitFilterReqDTO filter
    ) {
        return ResponseEntity.ok(
                benefitService.findBenefit(filter)
        );
    }

    //카테고리 필터
    @GetMapping("/categories")
    public ResponseEntity<List<BenefitCategoryResDTO>> getBenefitCategories() {
        return ResponseEntity.ok(
                benefitService.findBenefitCategories()
        );
    }


    //지역 필터
    @GetMapping("/regions")
    public ResponseEntity<List<BenefitRegionResDTO>> getRegion(
            @RequestParam(required = false)
            String parentRegionCode
    ) {
        return ResponseEntity.ok(
                benefitService.findRegion(parentRegionCode)
        );
    }

    //전공 필터
    @GetMapping("/majors")
    public ResponseEntity<List<BenefitMajorResDTO>> getBenefitMajors() {
        return ResponseEntity.ok(
                benefitService.findBenefitMajors()
        );
    }

    // 학력 필터
    @GetMapping("/schools")
    public ResponseEntity<List<BenefitSchoolResDTO>>
    getBenefitSchools() {
        return ResponseEntity.ok(
                benefitService.findBenefitSchools()
        );
    }

    // 직업 필터
    @GetMapping("/jobs")
    public ResponseEntity<List<BenefitJobResDTO>>
    getBenefitJobs() {
        return ResponseEntity.ok(
                benefitService.findBenefitJobs()
        );
    }

    //혼인 필터
    // 혼인 여부 필터
    @GetMapping("/marriage")
    public ResponseEntity<List<BenefitMarriageResDTO>>
    getBenefitMarriage() {
        return ResponseEntity.ok(
                benefitService.findBenefitMarriage()
        );
    }

    // 추천 검색어 조회
    @GetMapping("/search/recommended")
    public ResponseEntity<List<RecommendedKeywordResDTO>>
    getRecommendedKeywords() {

        return ResponseEntity.ok(
                benefitService.findRecommendedKeywords()
        );
    }

    // 최근 검색어 조회
    @GetMapping("/search/recent")
    public ResponseEntity<List<String>> getRecentKeywords(
            Principal principal
    ) {
        if (principal == null) {
            return ResponseEntity.ok(
                    Collections.emptyList()
            );
        }

        Integer memberNo =
                memberMapper.findMemberNoByLoginId(
                        principal.getName()
                );

        if (memberNo == null) {
            return ResponseEntity.ok(
                    Collections.emptyList()
            );
        }

        return ResponseEntity.ok(
                benefitService.findRecentKeywords(
                        memberNo
                )
        );
    }

    // 최근 검색어 저장
    @PostMapping("/search/recent")
    public ResponseEntity<Void> saveRecentKeyword(
            @RequestBody RecentKeywordReqDTO requestDTO,
            Principal principal
    ) {
        if (principal == null) {
            return ResponseEntity.noContent().build();
        }

        Integer memberNo =
                memberMapper.findMemberNoByLoginId(
                        principal.getName()
                );

        if (memberNo != null) {
            benefitService.saveRecentKeyword(
                    memberNo,
                    requestDTO.getKeyword()
            );
        }

        return ResponseEntity.noContent().build();
    }

    // 최근 검색어 한 개 삭제
    @DeleteMapping("/search/recent")
    public ResponseEntity<Void> deleteRecentKeyword(
            @RequestParam String keyword,
            Principal principal
    ) {
        if (principal == null) {
            return ResponseEntity.noContent().build();
        }

        Integer memberNo =
                memberMapper.findMemberNoByLoginId(
                        principal.getName()
                );

        if (memberNo != null) {
            benefitService.deleteRecentKeyword(
                    memberNo,
                    keyword
            );
        }

        return ResponseEntity.noContent().build();
    }

    // 최근 검색어 전체 삭제
    @DeleteMapping("/search/recent/all")
    public ResponseEntity<Void> deleteAllRecentKeywords(
            Principal principal
    ) {if (principal == null) {
            return ResponseEntity.noContent().build();}

        Integer memberNo =
                memberMapper.findMemberNoByLoginId(
                        principal.getName());
        if (memberNo != null) {
            benefitService.deleteAllRecentKeywords(
                    memberNo);}
        return ResponseEntity.noContent().build();}

    // 혜택 상세 조회
    @GetMapping("/{benefitNo}")
    public ResponseEntity<BenefitDetailResDTO>
    getBenefitDetail(
            @PathVariable Integer benefitNo
    ) {
        return ResponseEntity.ok(
                benefitService.findBenefitDetail(
                        benefitNo
                )
        );
    }

    //사용자프로필 조건기반 혜택추천
    @GetMapping("/profile-filter")
    public ResponseEntity<BenefitProfileFilterResDTO>
    getBenefitProfileFilter(
            Principal principal
    ) {
        if (principal == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        Integer memberNo =
                memberMapper.findMemberNoByLoginId(
                        principal.getName()
                );

        if (memberNo == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .build();
        }

        return ResponseEntity.ok(
                benefitService.findBenefitProfileFilter(
                        memberNo
                )
        );
    }

    //소비 기반 혜택 추천
    @GetMapping("/recommend/consumption")
    public ResponseEntity<ConsumptionRecommendResDTO>
    getConsumptionRecommendation(
            Principal principal,
            BenefitFilterReqDTO filter
    ) {
        if (principal == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        Integer memberNo =
                memberMapper.findMemberNoByLoginId(
                        principal.getName()
                );

        return ResponseEntity.ok(
                benefitService
                        .findConsumptionRecommendedBenefits(
                                memberNo,
                                filter
                        )
        );
    }

}