package org.scoula.benefit.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scoula.benefit.dto.YouthPolicyRequestDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
@Slf4j
public class YouthPolicyApiClient {

    @Value("${youthcenter.api.url}")
    private String apiUrl;

    @Value("${youthcenter.api.key}")
    private String apiKey;

    public String getPoliciesRaw(
            YouthPolicyRequestDTO requestDTO
    ) {
        RestTemplate restTemplate = new RestTemplate();

        restTemplate.getMessageConverters()
                .add(
                        0,
                        new StringHttpMessageConverter(
                                StandardCharsets.UTF_8
                        )
                );

        String url = buildUrl(requestDTO);

        try {
            log.info(
                    "온통청년 API 요청: pageNum={}, pageSize={}",
                    requestDTO.getPageNum(),
                    requestDTO.getPageSize()
            );

            return restTemplate.getForObject(
                    url,
                    String.class
            );

        } catch (HttpStatusCodeException e) {
            /*
             * 외부 서버가 4xx, 5xx 응답을 반환한 경우
             *
             * e.getResponseBodyAsString()을 출력하면
             * 온통청년 HTML 오류 페이지 전체가 로그에 남으므로
             * 상태 코드만 기록한다.
             */
            log.error(
                    "온통청년 API 요청 실패: status={}, pageNum={}, pageSize={}",
                    e.getStatusCode(),
                    requestDTO.getPageNum(),
                    requestDTO.getPageSize()
            );

            throw new IllegalStateException(
                    "온통청년 API 요청에 실패했습니다. status="
                            + e.getStatusCode()
                            + ", pageNum="
                            + requestDTO.getPageNum(),
                    e
            );

        } catch (RestClientException e) {
            /*
             * 연결 실패, 타임아웃 등 HTTP 통신 과정에서 발생한 오류
             */
            log.error(
                    "온통청년 API 통신 실패: pageNum={}, pageSize={}, exception={}",
                    requestDTO.getPageNum(),
                    requestDTO.getPageSize(),
                    e.getClass().getSimpleName()
            );

            throw new IllegalStateException(
                    "온통청년 API 통신에 실패했습니다. pageNum="
                            + requestDTO.getPageNum(),
                    e
            );
        }
    }

    private String buildUrl(
            YouthPolicyRequestDTO requestDTO
    ) {
        UriComponentsBuilder builder =
                UriComponentsBuilder
                        .fromHttpUrl(apiUrl)
                        .queryParam("apiKeyNm", apiKey)
                        .queryParam(
                                "pageNum",
                                requestDTO.getPageNum()
                        )
                        .queryParam(
                                "pageSize",
                                requestDTO.getPageSize()
                        )
                        .queryParam(
                                "rtnType",
                                requestDTO.getRtnType()
                        );

        if (hasText(requestDTO.getPlcyNm())) {
            builder.queryParam(
                    "plcyNm",
                    requestDTO.getPlcyNm()
            );
        }

        if (hasText(requestDTO.getPlcyKywdNm())) {
            builder.queryParam(
                    "plcyKywdNm",
                    requestDTO.getPlcyKywdNm()
            );
        }

        if (hasText(requestDTO.getLclsfNm())) {
            builder.queryParam(
                    "lclsfNm",
                    requestDTO.getLclsfNm()
            );
        }

        if (hasText(requestDTO.getMclsfNm())) {
            builder.queryParam(
                    "mclsfNm",
                    requestDTO.getMclsfNm()
            );
        }

        if (hasText(requestDTO.getZipCd())) {
            builder.queryParam(
                    "zipCd",
                    requestDTO.getZipCd()
            );
        }

        if (hasText(requestDTO.getPlcyNo())) {
            builder.queryParam(
                    "plcyNo",
                    requestDTO.getPlcyNo()
            );
        }

        return builder
                .build(false)
                .toUriString();
    }

    private boolean hasText(String value) {
        return value != null
                && !value.trim().isEmpty();
    }
}