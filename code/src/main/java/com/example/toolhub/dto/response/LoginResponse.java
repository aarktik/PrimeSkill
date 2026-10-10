package com.example.toolhub.dto.response;

import com.example.toolhub.domain.enums.Role;

public record LoginResponse(
        Long id,
        String email,
        Role role
) {
}