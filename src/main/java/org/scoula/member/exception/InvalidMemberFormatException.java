package org.scoula.member.exception;

public class InvalidMemberFormatException extends RuntimeException {
    public InvalidMemberFormatException(String message) {
        super(message);
    }
}