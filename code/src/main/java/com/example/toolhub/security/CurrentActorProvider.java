package com.example.toolhub.security;

import com.example.toolhub.exception.AuthenticationRequiredException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Shared adapter between Spring Security and application services. */
@Component
public class CurrentActorProvider {

    public CurrentActor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticated(authentication)) {
            return new CurrentActor(null, false);
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUserPrincipal user) || user.getId() == null) {
            return new CurrentActor(null, false);
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ADMIN".equals(authority.getAuthority()));
        return new CurrentActor(user.getId(), admin);
    }

    public CurrentActor requireActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticated(authentication)) {
            throw new AuthenticationRequiredException();
        }
        CurrentActor actor = currentActor();
        if (actor.id() == null) {
            throw new AccessDeniedException("Authenticated principal does not implement the required user-id contract");
        }
        return actor;
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }
}