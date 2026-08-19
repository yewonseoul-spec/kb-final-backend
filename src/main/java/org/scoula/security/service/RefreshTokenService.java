package org.scoula.security.service;

public interface RefreshTokenService {
    void save(int memberNo, String refreshToken);
    boolean matches(int memberNo, String refreshToken);
    void delete(int memberNo);
}