package com.example.toolhub.service.impl;

import com.example.toolhub.domain.entity.User;
import com.example.toolhub.dto.request.RegisterRequest;
import com.example.toolhub.dto.response.UserProfileResponse;
import com.example.toolhub.exception.DuplicateResourceException;
import com.example.toolhub.mapper.UserMapper;
import com.example.toolhub.repository.UserRepository;
import com.example.toolhub.service.UserRegistrationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserRegistrationServiceImpl implements UserRegistrationService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationServiceImpl(
            UserRepository userRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException(
                    "Email is already registered"
            );
        }

        String encodedPassword =
                passwordEncoder.encode(request.password());

        User user = userMapper.toEntity(
                request,
                normalizedEmail,
                encodedPassword
        );

        try {
            User savedUser = userRepository.saveAndFlush(user);
            return userMapper.toResponse(savedUser);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException(
                    "Email is already registered"
            );
        }
    }
}