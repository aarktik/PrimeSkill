package com.example.toolhub.security;

import com.example.toolhub.domain.entity.User;
import com.example.toolhub.domain.enums.Role;
import com.example.toolhub.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private JpaUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new JpaUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsername_withExistingAdmin_returnsUserDetails() {
        User user = new User("admin@example.com", "encoded-password");
        user.setRole(Role.ADMIN);
        user.setEnabled(true);

        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(user));

        UserDetails result =
                userDetailsService.loadUserByUsername("admin@example.com");

        assertThat(result.getUsername()).isEqualTo("admin@example.com");
        assertThat(result.getPassword()).isEqualTo("encoded-password");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");

        verify(userRepository).findByEmail("admin@example.com");
    }

    @Test
    void loadUserByUsername_withUnknownEmail_throwsException() {
        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userDetailsService.loadUserByUsername("missing@example.com")
        ).isInstanceOf(UsernameNotFoundException.class);
    }
}