package com.example.toolhub.security;

import static org.junit.jupiter.api.Assertions.*;

import com.example.toolhub.domain.entity.User;
import com.example.toolhub.domain.enums.Role;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class UserPrincipalSerializationTest {
    @ParameterizedTest
    @EnumSource(Role.class)
    void sessionRoundTripPreservesIdentityWithoutPasswordHash(Role role) throws Exception {
        var user = new User("serialization@example.test", "synthetic-hash-must-not-be-persisted");
        var id = User.class.getDeclaredField("id"); id.setAccessible(true); id.set(user, 42L);
        user.setEmail("serialization@example.test");
        user.setPasswordHash("synthetic-hash-must-not-be-persisted");
        user.setRole(role);
        user.setEnabled(true);
        var principal = new UserPrincipal(user);
        assertEquals(user.getPasswordHash(), principal.getPassword(), "Fresh login still needs the hash");
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(principal); }
        assertFalse(new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)
                .contains(user.getPasswordHash()), "Session payload must not contain the password hash");
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var restored = (UserPrincipal) in.readObject();
            assertEquals(42L, restored.getId());
            assertEquals(user.getEmail(), restored.getUsername());
            assertEquals(role, restored.getRole());
            assertTrue(restored.isEnabled());
            assertNull(restored.getPassword());
            assertEquals(principal.getAuthorities(), restored.getAuthorities());
        }
    }
}
