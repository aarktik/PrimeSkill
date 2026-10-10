package com.example.toolhub.controller.web;

import com.example.toolhub.dto.request.UpdateUserProfileRequest;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.UserProfileService;
import jakarta.validation.Validator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileWebController {
    private final UserProfileService profiles;
    private final CurrentActorProvider actors;
    private final SpringValidatorAdapter validator;

    public ProfileWebController(UserProfileService profiles, CurrentActorProvider actors, Validator validator) {
        this.profiles = profiles; this.actors = actors; this.validator = new SpringValidatorAdapter(validator);
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        var profile = profiles.getMyProfile(actors.requireActor().id());
        model.addAttribute("profileRequest", new UpdateUserProfileRequest(profile.displayName(), profile.bio(), profile.avatarUrl()));
        return populate(model);
    }

    @PostMapping("/profile")
    public String save(@RequestParam(defaultValue = "") String displayName,
                       @RequestParam(required = false) String bio, @RequestParam(required = false) String avatarUrl,
                       Model model, RedirectAttributes redirect) {
        var actor = actors.requireActor();
        // Optional URLs must be null before Bean Validation, rather than an invalid blank string.
        var request = new UpdateUserProfileRequest(displayName.trim(), optional(bio), optional(avatarUrl));
        var errors = new BeanPropertyBindingResult(request, "profileRequest");
        validator.validate(request, errors);
        if (errors.hasErrors()) {
            model.addAttribute("profileRequest", request);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "profileRequest", errors);
            return populate(model);
        }
        profiles.updateMyProfile(actor.id(), request);
        redirect.addFlashAttribute("successMessage", "Your profile has been updated.");
        return "redirect:/profile";
    }

    private String populate(Model model) {
        model.addAttribute("profile", profiles.getMyProfile(actors.requireActor().id()));
        model.addAttribute("pageTitle", "Your profile"); model.addAttribute("activeNav", "profile");
        return "profile/form";
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
