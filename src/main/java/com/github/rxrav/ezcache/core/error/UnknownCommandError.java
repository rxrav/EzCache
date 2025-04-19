package com.github.rxrav.ezcache.core.error;

public class UnknownCommandError extends RuntimeException {
    public UnknownCommandError(String message) {
        super("UNKCMDERR ".concat(message));
    }
}
