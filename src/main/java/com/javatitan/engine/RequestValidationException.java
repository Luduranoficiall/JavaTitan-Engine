package com.javatitan.engine;

public class RequestValidationException extends RuntimeException {
    private final int status;

    public RequestValidationException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
