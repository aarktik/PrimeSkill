package com.example.toolhub.service;

import com.example.toolhub.domain.entity.User;
import com.example.toolhub.domain.entity.UserProfile;
import com.example.toolhub.domain.enums.Role;
import com.example.toolhub.dto.request.UpdateUserProfileRequest;
import com.example.toolhub.dto.response.UserProfileResponse;
import com.example.toolhub.exception.ResourceNotFoundException;
import com.example.toolhub.mapper.UserMapper;
import com.example.toolhub.repository.UserRepository;
import com.example.toolhub.service.impl.UserProfileServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    private UserProfileService userProfileService;
    private User user;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileServiceImpl(
                userRepository,
                userMapper
        );

        user = new User("user@example.com", "encoded-password");
        user.attachProfile(new UserProfile(
                "Old Name",
                "Old bio",
                null
        ));
    }

    @Test
    void getMyProfile_withExistingUser_returnsProfile() {
        UserProfileResponse expected = new UserProfileResponse(
                1L,
                "user@example.com",
                Role.USER,
                "Old Name",
                "Old bio",
                null,
                null
        );

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));
        when(userMapper.toResponse(user))
                .thenReturn(expected);

        UserProfileResponse result =
                userProfileService.getMyProfile(1L);

        assertThat(result).isEqualTo(expected);
        verify(userRepository).findById(1L);
    }

    @Test
    void updateMyProfile_withExistingUser_updatesProfile() {
        UpdateUserProfileRequest request =
                new UpdateUserProfileRequest(
                        " New Name ",
                        " New bio ",
                        "https://example.com/avatar.png"
                );

        UserProfileResponse expected = new UserProfileResponse(
                1L,
                "user@example.com",
                Role.USER,
                "New Name",
                "New bio",
                "https://example.com/avatar.png",
                null
        );

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));
        when(userMapper.toResponse(user))
                .thenReturn(expected);

        UserProfileResponse result =
                userProfileService.updateMyProfile(1L, request);

        assertThat(user.getProfile().getDisplayName())
                .isEqualTo("New Name");
        assertThat(user.getProfile().getBio())
                .isEqualTo("New bio");
        assertThat(user.getProfile().getAvatarUrl())
                .isEqualTo("https://example.com/avatar.png");
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getMyProfile_withUnknownUser_throwsNotFoundException() {
        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userProfileService.getMyProfile(99L)
        ).isInstanceOf(ResourceNotFoundException.class);
    }
}