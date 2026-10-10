package com.example.toolhub.controller.web;

import com.example.toolhub.security.CurrentActorProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import com.example.toolhub.security.AuthenticatedUserPrincipal;
import com.example.toolhub.security.CurrentActor;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@ControllerAdvice(basePackages = "com.example.toolhub.controller.web", assignableTypes = BasicErrorController.class)
public class WebLayoutAdvice {
    private final CurrentActorProvider actors;
    private final HttpSessionSecurityContextRepository errorContexts = new HttpSessionSecurityContextRepository();
    public WebLayoutAdvice(CurrentActorProvider actors) { this.actors = actors; }
    @ModelAttribute
    public void layout(Model model, HttpServletRequest request) {
        var actor = actors.currentActor();
        // Error dispatch can clear the holder; render menus from the existing session only.
        if (request.getDispatcherType() == DispatcherType.ERROR && (actor == null || actor.id() == null)) {
            var authentication = errorContexts.loadDeferredContext(request).get().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal
                    && principal.getId() != null) {
                boolean admin = authentication.getAuthorities().stream().anyMatch(authority ->
                        "ROLE_ADMIN".equals(authority.getAuthority()) || "ADMIN".equals(authority.getAuthority()));
                actor = new CurrentActor(principal.getId(), admin);
            }
        }
        model.addAttribute("signedIn", actor != null && actor.id() != null);
        model.addAttribute("isAdmin", actor != null && actor.admin());
        model.addAttribute("viewerId", actor == null ? null : actor.id());
    }
}
