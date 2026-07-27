package org.scoula.benefit.client;

import lombok.RequiredArgsConstructor;
import org.scoula.benefit.dto.YouthPolicyRequestDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class YouthPolicyApiClient {

    @Value("${youthcenter.api.url}")
    private String apiUrl;

    @Value("${youthcenter.api.key}")
    private String apiKey;

    public String getPoliciesRaw(YouthPolicyRequestDTO requestDTO) {
        RestTemplate restTemplate = new RestTemplate();

        restTemplate.getMessageConverters()
                .add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));

        String url = buildUrl(requestDTO);

        System.out.println("온통청년 API 요청 URL = " + url);

        return restTemplate.getForObject(url, String.class);
    }


    private String buildUrl(YouthPolicyRequestDTO requestDTO) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(apiUrl)
                .queryParam("apiKeyNm", apiKey)
                .queryParam("pageNum", requestDTO.getPageNum())
                .queryParam("pageSize", requestDTO.getPageSize())
                .queryParam("rtnType", requestDTO.getRtnType());

             if (hasText(requestDTO.getPlcyNm())) {
            builder.queryParam("plcyNm", requestDTO.getPlcyNm());
        }

        if (hasText(requestDTO.getPlcyKywdNm())) {
            builder.queryParam("plcyKywdNm", requestDTO.getPlcyKywdNm());
        }

        if (hasText(requestDTO.getLclsfNm())) {
            builder.queryParam("lclsfNm", requestDTO.getLclsfNm());
        }

        if (hasText(requestDTO.getMclsfNm())) {
            builder.queryParam("mclsfNm", requestDTO.getMclsfNm());
        }

        if (hasText(requestDTO.getZipCd())) {
            builder.queryParam("zipCd", requestDTO.getZipCd());
        }

        if (hasText(requestDTO.getPlcyNo())) {
            builder.queryParam("plcyNo", requestDTO.getPlcyNo());
        }

        return builder.build(false).toUriString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}