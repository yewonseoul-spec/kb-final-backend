package org.scoula.member.exception;

public class PasswordMissmatchException extends RuntimeException {
    public PasswordMissmatchException() {
        super("현재 비밀번호가 올바르지 않습니다.");
    }
}
