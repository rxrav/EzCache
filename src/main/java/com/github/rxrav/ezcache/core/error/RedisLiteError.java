package com.github.rxrav.ezcache.core.error;

public class RedisLiteError extends RuntimeException {
    public RedisLiteError(String message) {  super("ERR ".concat(message)); }
}
