package com.example.toolhub.service.impl;

import com.example.toolhub.domain.entity.User;
import com.example.toolhub.domain.entity.UserProfile;
import com.example.toolhub.dto.request.UpdateUserProfileRequest;
import com.example.toolhub.dto.response.UserProfileResponse;
import com.example.toolhub.exception.ResourceNotFoundException;
import com.example.toolhub.mapper.UserMapper;
import com.example.toolhub.repository.UserRepository;
import com.example.toolhub.service.UserProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserProfileServiceImpl(
            UserRepository userRepository,
            UserMapper userMapper
    ) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(Long userId) {
        User user = findUser(userId);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(
            Long userId,
            UpdateUserProfileRequest request
    ) {
        User user = findUser(userId);
        UserProfile profile = user.getProfile();

        if (profile == null) {
            throw new ResourceNotFoundException("User profile not found");
        }

        profile.update(
                request.displayName().trim(),
                trimToNull(request.bio()),
                trimToNull(request.avatarUrl())
        );

        return userMapper.toResponse(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}