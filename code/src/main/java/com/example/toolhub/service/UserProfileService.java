package com.example.toolhub.service;

import com.example.toolhub.dto.request.UpdateUserProfileRequest;
import com.example.toolhub.dto.response.UserProfileResponse;

public interface UserProfileService {

    UserProfileResponse getMyProfile(Long userId);

    UserProfileResponse updateMyProfile(
            Long userId,
            UpdateUserProfileRequest request
    );
}