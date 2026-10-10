package com.example.toolhub.controller.api;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import com.example.toolhub.dto.request.LoginRequest;
import com.example.toolhub.dto.request.RegisterRequest;
import com.example.toolhub.dto.response.LoginResponse;
import com.example.toolhub.dto.response.UserProfileResponse;
import com.example.toolhub.security.UserPrincipal;
import com.example.toolhub.service.UserRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRegistrationService userRegistrationService;
    private final AuthenticationManager authenticationManager;

    public AuthController(
            UserRegistrationService userRegistrationService,
            AuthenticationManager authenticationManager
    ) {
        this.userRegistrationService = userRegistrationService;
        this.authenticationManager = authenticationManager;
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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        email,
                        request.password()
                )
        );
        if (servletRequest.getSession(false) != null) {
        servletRequest.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        servletRequest.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );

        UserPrincipal user = (UserPrincipal) authentication.getPrincipal();

        return ResponseEntity.ok(
                new LoginResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getRole()
                )
        );
    }
    @PostMapping("/logout")
public ResponseEntity<Void> logout(
        HttpServletRequest servletRequest
) {
    HttpSession session = servletRequest.getSession(false);

    if (session != null) {
        session.invalidate();
    }

    SecurityContextHolder.clearContext();

    return ResponseEntity.noContent().build();
}
@GetMapping("/csrf")
public CsrfToken csrf(CsrfToken csrfToken) {
    return csrfToken;
}
}
