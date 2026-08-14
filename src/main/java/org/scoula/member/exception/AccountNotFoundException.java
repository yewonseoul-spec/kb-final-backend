package org.scoula.member.exception;

// 아이디 찾기·비밀번호 재설정에서 조건에 맞는 계정이 없을 때.
// 어느 항목이 틀렸는지 드러내면 계정 열거에 쓰이므로, 메시지는 호출부에서 리터럴로 넘긴다.
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}