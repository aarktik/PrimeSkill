package com.example.toolhub.exception;

public class InvalidRequestParameterException extends RuntimeException {
    public InvalidRequestParameterException(String message) {
        super(message);
    }
}
