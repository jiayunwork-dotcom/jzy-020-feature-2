package com.flamelab.job;

/** Raised when a solve request fails input validation; mapped to HTTP 400. */
public class InputValidationException extends RuntimeException {

    private final ErrorType type;

    public InputValidationException(ErrorType type, String message) {
        super(message);
        this.type = type;
    }

    public ErrorType type() {
        return type;
    }
}
