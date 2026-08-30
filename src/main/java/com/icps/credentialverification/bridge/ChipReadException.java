package com.icps.credentialverification.bridge;

public class ChipReadException extends RuntimeException {

    private final int statusCode;

    public ChipReadException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
