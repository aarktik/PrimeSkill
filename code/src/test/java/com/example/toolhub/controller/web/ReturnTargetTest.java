package com.example.toolhub.controller.web;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReturnTargetTest {
    @ParameterizedTest @ValueSource(strings={"/profile", "/tools/code-companion#reviews-heading", "/tools?q=hello%20world&sort=rating", "/dashboard/tools/123/tags", "/admin/tags/3/edit"})
    void preservesValidApplicationDestinations(String target) { assertEquals(target, ReturnTarget.safe(target)); }

    @ParameterizedTest @ValueSource(strings={"https://evil.test", "//evil.test", "/\\evil.test", "/%2f%2fevil.test", "/tools/../login", "/api/v1/auth/logout", "/login", "/tools?x=%0D%0Aevil", "javascript:alert(1)"})
    void rejectsExternalAndUnsupportedDestinations(String target) { assertEquals(ReturnTarget.DEFAULT, ReturnTarget.safe(target)); }
}
