package org.scoula.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Log4j2
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    // 토큰 수명과 같게 둔다. 만료되면 Redis가 알아서 지운다
    private static final Duration TTL = Duration.ofDays(7);

    // 설계서(테이블 정의서)에 정의된 키 형식
    private String key(int memberNo) {
        return "auth:refresh:" + memberNo;
    }

    @Override
    public void save(int memberNo, String refreshToken) {
        redisTemplate.opsForValue().set(key(memberNo), refreshToken, TTL);
    }

    @Override
    public boolean matches(int memberNo, String refreshToken) {
        String saved = redisTemplate.opsForValue().get(key(memberNo));
        return saved != null && saved.equals(refreshToken);
    }

    @Override
    public void delete(int memberNo) {
        redisTemplate.delete(key(memberNo));
    }
}