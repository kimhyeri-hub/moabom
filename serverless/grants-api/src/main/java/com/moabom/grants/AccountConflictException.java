package com.moabom.grants;

public final class AccountConflictException extends RuntimeException {
    public AccountConflictException(String message) {
        super(message);
    }
}
