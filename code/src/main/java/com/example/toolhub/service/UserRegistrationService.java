package com.example.toolhub.service;

import com.example.toolhub.dto.request.RegisterRequest;
import com.example.toolhub.dto.response.UserProfileResponse;

public interface UserRegistrationService {

    UserProfileResponse register(RegisterRequest request);
}