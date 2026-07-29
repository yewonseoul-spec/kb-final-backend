package org.scoula.exception;

import lombok.extern.log4j.Log4j2;
import org.scoula.member.exception.RequiredTermsNotAgreedException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.NoSuchElementException;

@RestControllerAdvice
@Log4j2
@Order(2)
public class ApiExceptionAdvice {
    // 404 에러
    @ExceptionHandler(NoSuchElementException.class)
    protected ResponseEntity<String> handleIllegalArgumentException(NoSuchElementException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("해당 ID의 요소가 없습니다.");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<String> handle404(NoHandlerFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("해당 URL이 없습니다.");
    }

    // 400 에러 - 필수 약관 미동의
    @ExceptionHandler(RequiredTermsNotAgreedException.class)
    protected ResponseEntity<String> handleRequiredTermsNotAgreed(RequiredTermsNotAgreedException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body(e.getMessage());
    }

    // 400 에러 - 필수 요청 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    protected ResponseEntity<String> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("필수 파라미터가 누락되었습니다: " + e.getParameterName());
    }

    // 409 에러 - UNIQUE 제약 위반 (아이디/이메일 중복)
    @ExceptionHandler(DuplicateKeyException.class)
    protected ResponseEntity<String> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("중복 키 위반", e);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("이미 사용 중인 아이디 또는 이메일입니다.");
    }

    // 500 에러
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<String> handleException(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body("서버 오류가 발생했습니다.");
    }
}
