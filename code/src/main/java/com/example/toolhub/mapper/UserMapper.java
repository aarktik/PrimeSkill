package com.example.toolhub.mapper;

import com.example.toolhub.domain.entity.User;
import com.example.toolhub.domain.entity.UserProfile;
import com.example.toolhub.dto.request.RegisterRequest;
import com.example.toolhub.dto.response.UserProfileResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(
            RegisterRequest request,
            String normalizedEmail,
            String encodedPassword
    ) {
        User user = new User(normalizedEmail, encodedPassword);

        UserProfile profile = new UserProfile(
                request.displayName().trim(),
                null,
                null
        );

        user.attachProfile(profile);
        return user;
    }

    public UserProfileResponse toResponse(User user) {
        UserProfile profile = user.getProfile();

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                profile.getDisplayName(),
                profile.getBio(),
                profile.getAvatarUrl(),
                user.getCreatedAt()
        );
    }
}