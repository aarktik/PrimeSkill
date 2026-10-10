package com.example.toolhub.security;

/**
 * Minimal principal contract consumed by feature modules.
 * Role A's UserDetails implementation must implement this interface.
 */
public interface AuthenticatedUserPrincipal {
    Long getId();
}
