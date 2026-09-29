package com.example.toolhub.controller.web;

import com.example.toolhub.security.CurrentActorProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice(basePackages = "com.example.toolhub.controller.web")
public class WebLayoutAdvice {
    private final CurrentActorProvider actors;
    public WebLayoutAdvice(CurrentActorProvider actors) { this.actors = actors; }
    @ModelAttribute
    public void layout(Model model) {
        var actor = actors.currentActor();
        model.addAttribute("signedIn", actor != null && actor.id() != null);
        model.addAttribute("isAdmin", actor != null && actor.admin());
        model.addAttribute("viewerId", actor == null ? null : actor.id());
    }
}