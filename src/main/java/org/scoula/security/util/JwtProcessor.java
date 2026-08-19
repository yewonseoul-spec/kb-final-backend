package org.scoula.security.util;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtProcessor {
    static private final long TOKEN_VALID_MILISECOND = 1000L * 60 * 60; // 토큰 유효기간 60분

    // 리프레시 토큰 유효기간 7일
    static private final long REFRESH_VALID_MILISECOND = 1000L * 60 * 60 *
            24 * 7;

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_REFRESH = "refresh";
    private static final String CLAIM_MEMBER_NO = "memberNo";

    private final Key key;

    public JwtProcessor(@Value("${jwt.secret}") String secretKey) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // JWT 생성
    public String generateToken(String subject) {
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(new Date().getTime() + TOKEN_VALID_MILISECOND))
                .signWith(key)
                .compact();
    }

    // JWT Subject(username) 추출 - 해석 불가인 경우 예외 발생
    // 예외 ExpiredJwtException, UnsupportedJwtException, MalformedJwtException, SignatureException,
    //      IllegalArgumentException
    public String getUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // JWT 검증(유효 기간 검증) - 해석 불가인 경우 예외 발생
    public boolean validateToken(String token) {
        Jws<Claims> claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
        return true;
    }

    // 리프레시 토큰 생성. Redis 키가 회원번호라 토큰 안에 함께 넣는다
    public String generateRefreshToken(String subject, int memberNo) {
        return Jwts.builder()
                .setSubject(subject)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .claim(CLAIM_MEMBER_NO, memberNo)
                .setIssuedAt(new Date())
                .setExpiration(new Date(new Date().getTime() +
                        REFRESH_VALID_MILISECOND))
                .signWith(key)
                .compact();
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(getClaims(token).get(CLAIM_TYPE));
    }

    public int getMemberNo(String token) {
        return getClaims(token).get(CLAIM_MEMBER_NO, Integer.class);
    }
}
