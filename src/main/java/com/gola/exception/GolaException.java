package com.gola.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class GolaException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public GolaException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public static GolaException notFound(String resource) {
        return new GolaException(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " not found");
    }
    public static GolaException forbidden() {
        return new GolaException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }
    public static GolaException forbidden(String msg) {
        return new GolaException(HttpStatus.FORBIDDEN, "FORBIDDEN", msg);
    }
    public static GolaException badRequest(String msg) {
        return new GolaException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", msg);
    }
    public static GolaException conflict(String msg) {
        return new GolaException(HttpStatus.CONFLICT, "CONFLICT", msg);
    }
    public static GolaException tooManyRequests(String msg) {
        return new GolaException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", msg);
    }
    public static GolaException unauthorized(String msg) {
        return new GolaException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", msg);
    }
}
