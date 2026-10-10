package com.example.toolhub.service;

import com.example.toolhub.domain.entity.User;
import com.example.toolhub.domain.enums.Role;
import com.example.toolhub.dto.request.RegisterRequest;
import com.example.toolhub.dto.response.UserProfileResponse;
import com.example.toolhub.exception.DuplicateResourceException;
import com.example.toolhub.mapper.UserMapper;
import com.example.toolhub.repository.UserRepository;
import com.example.toolhub.service.impl.UserRegistrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new UserRegistrationServiceImpl(
                userRepository,
                userMapper,
                passwordEncoder
        );
    }

    @Test
    void register_normalizesEmailHashesPasswordAndReturnsResponse() {
        RegisterRequest request = new RegisterRequest(
                "  USER@Example.COM  ",
                "password123",
                "Nattakorn"
        );

        User user = new User("user@example.com", "bcrypt-hash");

        UserProfileResponse expected = new UserProfileResponse(
                1L,
                "user@example.com",
                Role.USER,
                "Nattakorn",
                null,
                null,
                Instant.parse("2026-09-21T00:00:00Z")
        );

        when(userRepository.existsByEmail("user@example.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("password123"))
                .thenReturn("bcrypt-hash");
        when(userMapper.toEntity(
                request,
                "user@example.com",
                "bcrypt-hash"
        )).thenReturn(user);
        when(userRepository.saveAndFlush(user))
                .thenReturn(user);
        when(userMapper.toResponse(user))
                .thenReturn(expected);

        UserProfileResponse actual = registrationService.register(request);

        assertSame(expected, actual);

        verify(userRepository).existsByEmail("user@example.com");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void register_whenEmailAlreadyExistsThrowsConflictBeforeHashing() {
        RegisterRequest request = new RegisterRequest(
                "USER@example.com",
                "password123",
                "Nattakorn"
        );

        when(userRepository.existsByEmail("user@example.com"))
                .thenReturn(true);

        assertThrows(
                DuplicateResourceException.class,
                () -> registrationService.register(request)
        );

        verify(passwordEncoder, never()).encode(request.password());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void register_whenDatabaseReportsDuplicateThrowsConflict() {
        RegisterRequest request = new RegisterRequest(
                "user@example.com",
                "password123",
                "Nattakorn"
        );

        User user = new User("user@example.com", "bcrypt-hash");

        when(userRepository.existsByEmail("user@example.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("password123"))
                .thenReturn("bcrypt-hash");
        when(userMapper.toEntity(
                request,
                "user@example.com",
                "bcrypt-hash"
        )).thenReturn(user);
        when(userRepository.saveAndFlush(user))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(
                DuplicateResourceException.class,
                () -> registrationService.register(request)
        );
    }
}