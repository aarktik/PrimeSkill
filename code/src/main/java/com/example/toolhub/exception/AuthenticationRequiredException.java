package com.example.toolhub.exception;

import org.springframework.security.core.AuthenticationException;

public class AuthenticationRequiredException extends AuthenticationException {
    public AuthenticationRequiredException() {
        super("Authentication is required");
    }
}