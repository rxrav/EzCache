package com.github.rxrav.ezcache.core.error;

public class ValidationError extends RuntimeException {
    public ValidationError(String message) {
        super("VALERR ".concat(message));
    }
}
