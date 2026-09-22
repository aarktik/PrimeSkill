package com.example.toolhub.controller.api;

import com.example.toolhub.dto.request.RegisterRequest;
import com.example.toolhub.dto.response.UserProfileResponse;
import com.example.toolhub.service.UserRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRegistrationService userRegistrationService;

    public AuthController(
            UserRegistrationService userRegistrationService
    ) {
        this.userRegistrationService = userRegistrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserProfileResponse> register(
           @Valid @RequestBody RegisterRequest request
    ) {
        UserProfileResponse response =
                userRegistrationService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}