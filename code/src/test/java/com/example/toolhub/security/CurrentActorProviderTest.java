package com.example.toolhub.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.toolhub.exception.AuthenticationRequiredException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentActorProviderTest {
    private final CurrentActorProvider provider = new CurrentActorProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireActor_whenAnonymous_throwsUnauthenticated() {
        assertThrows(AuthenticationRequiredException.class, provider::requireActor);
    }

    @Test
    void currentActor_whenPrincipalDoesNotImplementContract_doesNotTrustAdminAuthority() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("external-principal", "ignored", "ROLE_ADMIN"));

        CurrentActor actor = provider.currentActor();

        assertNull(actor.id());
        assertFalse(actor.admin());
    }

    @Test
    void currentActor_whenPrincipalImplementsContract_returnsTrustedIdAndRole() {
        AuthenticatedUserPrincipal principal = () -> 7L;
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, "ignored", "ROLE_ADMIN"));

        CurrentActor actor = provider.currentActor();

        assertEquals(7L, actor.id());
        assertEquals(true, actor.admin());
    }
}