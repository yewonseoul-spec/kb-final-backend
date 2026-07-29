package org.scoula.member.exception;

public class RequiredTermsNotAgreedException extends RuntimeException {
    public RequiredTermsNotAgreedException() {
        super("필수 약관에 모두 동의해야 합니다.");
    }
}
