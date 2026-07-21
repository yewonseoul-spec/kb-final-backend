package org.scoula.security.util;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.scoula.config.RootConfig;
import org.scoula.security.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {RootConfig.class, SecurityConfig.class})
@Log4j2
class JwtProcessorTest {
    @Autowired
    JwtProcessor jwtProcessor;

    @Test
    void generateToken() {
        String username = "user0";
        String token = jwtProcessor.generateToken(username);
        log.info(token);
        assertNotNull(token);
    }

    @Test
    void getUsername() {
        String token = "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ1c2VyMCIsImlhdCI6MTc4MjY5OTk3OCwiZXhwIjoxNzgyNzAwMjc4fQ.mtYw4o71LloEdg8ybo3CtU_s7mZAMViUn0wce5NNOFa7qmD7Yu-b9TcWScSiX_ne";

        String username = jwtProcessor.getUsername(token);
        log.info(username);
        assertNotNull(username);
    }


    @Test
    void validateToken() {
        // 5분 경과 후 테스트
        String token = "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ1c2VyMCIsImlhdCI6MTc4MjcwMDc0NywiZXhwIjoxNzgyNzAxMDQ3fQ.dtohdau99fMEm2Chvgk4ox4IzU21TiKqEv59cfKNQrU2oPm06CBOGbHjkdJTt-md";

        boolean isValid = jwtProcessor.validateToken(token); // 5분 경과 후면 예외 발생

        log.info(isValid);
        assertTrue(isValid);    // 5분전이면 true
    }

}