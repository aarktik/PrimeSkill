package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.toolhub.domain.entity.Category;
import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.enums.Role;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.repository.*;
import com.example.toolhub.support.PostgresTestDatabaseGuard;
import com.jayway.jsonpath.JsonPath;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Requires reviewed E revision/security dependencies; runs only on disposable PostgreSQL. */
@SpringBootTest(properties = "spring.datasource.url=${PRIMESKILL_TEST_DB_URL}")
@AutoConfigureMockMvc
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = PostgresTestDatabaseGuard.class)
class ToolMetadataContractPostgresIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ToolRepository tools;
    Client owner, admin, other;
    long tool, category, replacementCategory;
    String originalSlug, changedSlug;

    @BeforeEach void fixture() throws Exception {
        owner = registerAndLogin(false);
        admin = registerAndLogin(true);
        other = registerAndLogin(false);
        String suffix = UUID.randomUUID().toString();
        Category original = categories.saveAndFlush(new Category("Original " + suffix, "original-" + suffix, null));
        category = original.getId();
        replacementCategory = categories.saveAndFlush(new Category("Replacement " + suffix, "replacement-" + suffix, null)).getId();
        originalSlug = "tool-" + suffix;
        changedSlug = "changed-" + suffix;
        tool = tools.saveAndFlush(new Tool(owner.id(), original, "Original", originalSlug,
                "Original short", "Original details", "https://example.invalid/original")).getId();
        jdbc.update("update tools set view_count=17, review_revision=5 where id=?", tool);
    }

    @AfterEach void cleanup() {
        jdbc.update("delete from tools where id=?", tool);
        jdbc.update("delete from categories where id in (?,?)", category, replacementCategory);
        for (Client client : new Client[]{owner, admin, other})
            if (client != null) jdbc.update("delete from users where id=?", client.id());
    }

    static java.util.stream.Stream<Arguments> matrix() {
        return Arrays.stream(ToolStatus.values()).flatMap(status ->
                java.util.stream.Stream.of("owner", "admin", "other").map(actor -> Arguments.of(status, actor)));
    }

    @ParameterizedTest(name = "API {0}/{1}") @MethodSource("matrix")
    void apiMatrixUsesRealSessionAndPreservesDeniedRows(ToolStatus state, String actor) throws Exception {
        setState(state);
        Map<String, Object> before = snapshot();
        int expected = actor.equals("other") ? 403 : state == ToolStatus.DRAFT ? 200 : 409;
        ResultActions result = send(client(actor), put(api()), changedBody());
        result.andExpect(status().is(expected));
        if (expected == 200) {
            assertChanged();
            result.andExpect(jsonPath("$.reviewRevision").value(5))
                    .andExpect(jsonPath("$.ownerId").value(owner.id()))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        } else {
            result.andExpect(jsonPath("$.code").value(expected == 403 ? "ACCESS_DENIED" : "INVALID_STATE_TRANSITION"));
            assertEquals(before, snapshot(), "Denied write must leave every persisted field unchanged");
        }
    }

    @ParameterizedTest(name = "web {0}/{1}") @MethodSource("matrix")
    void webMatrixUsesSameServerGuard(ToolStatus state, String actor) throws Exception {
        setState(state);
        Map<String, Object> before = snapshot();
        int expected = actor.equals("other") ? 403 : state == ToolStatus.DRAFT ? 302 : 409;
        send(client(actor), webUpdate(), null).andExpect(status().is(expected));
        if (expected == 302) assertChanged(); else assertEquals(before, snapshot());
    }

    @ParameterizedTest @EnumSource(ToolStatus.class)
    void noOpStillRequiresDraft(ToolStatus state) throws Exception {
        setState(state);
        Map<String, Object> before = snapshot();
        String body = "{\"name\":\"Original\",\"slug\":\"" + originalSlug
                + "\",\"shortDescription\":\"Original short\",\"description\":\"Original details\",\"categoryId\":"
                + category + ",\"repositoryUrl\":\"https://example.invalid/original\"}";
        for (Client actor : new Client[]{owner, admin}) {
            send(actor, put(api()), body).andExpect(status().is(state == ToolStatus.DRAFT ? 200 : 409));
        }
        // Auditing may update updated_at on an allowed no-op; it is not an approval token.
        if (state != ToolStatus.DRAFT) assertEquals(before, snapshot());
        assertEquals(5L, snapshot().get("review_revision"));
        assertEquals(state.name(), snapshot().get("status"));
    }

    @Test void missingWrongAndCrossSessionCsrfAreDeniedWithoutMutation() throws Exception {
        Map<String, Object> before = snapshot();
        mvc.perform(put(api()).session(owner.session()).contentType(MediaType.APPLICATION_JSON).content(changedBody()))
                .andExpect(status().isForbidden());
        mvc.perform(put(api()).session(owner.session()).header(owner.header(), "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON).content(changedBody())).andExpect(status().isForbidden());
        mvc.perform(put(api()).session(owner.session()).header(admin.header(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content(changedBody())).andExpect(status().isForbidden());
        mvc.perform(webUpdate().session(owner.session())).andExpect(status().isForbidden());
        mvc.perform(webUpdate().session(owner.session()).header(admin.header(), admin.token()))
                .andExpect(status().isForbidden());
        assertEquals(before, snapshot());
    }

    @Test void anonymousWithValidCsrfAndLoggedOutClientCannotEdit() throws Exception {
        Map<String, Object> before = snapshot();
        Client anonymous = csrf(new MockHttpSession(), -1);
        send(anonymous, put(api()), changedBody()).andExpect(status().isUnauthorized());
        send(owner, post("/api/v1/auth/logout"), null).andExpect(status().isNoContent());
        Client loggedOut = csrf(new MockHttpSession(), owner.id());
        send(loggedOut, put(api()), changedBody()).andExpect(status().isUnauthorized());
        assertEquals(before, snapshot());
    }

    @Test void forgedActorCannotElevateOtherUser() throws Exception {
        Map<String, Object> before = snapshot();
        String forged = changedBody().replace("}", ",\"actorUserId\":" + owner.id()
                + ",\"actorIsAdmin\":true,\"ownerId\":" + other.id() + "}");
        send(other, put(api()), forged).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertEquals(before, snapshot());
    }

    @Test void draftValidationMissingCategoryAndDuplicateSlugDoNotPartiallyMutate() throws Exception {
        Map<String, Object> before = snapshot();
        send(owner, put(api()), changedBody().replace("https://example.invalid/changed", "javascript:alert(1)"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        send(owner, put(api()), changedBody().replace("\"categoryId\":" + replacementCategory, "\"categoryId\":9223372036854775807"))
                .andExpect(status().isNotFound());
        Tool duplicate = tools.saveAndFlush(new Tool(owner.id(), categories.findById(category).orElseThrow(),
                "Duplicate", changedSlug, "Short", "Details", null));
        try { send(owner, put(api()), changedBody()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT")); }
        finally { jdbc.update("delete from tools where id=?", duplicate.getId()); }
        assertEquals(before, snapshot());
    }

    @Test void editorOpenedBeforeSubmitCannotWriteAfterSubmit() throws Exception {
        send(owner, get("/dashboard/tools/" + tool + "/edit"), null).andExpect(status().isOk());
        send(owner, post("/api/v1/tools/" + tool + "/submit"), null).andExpect(status().isOk());
        Map<String, Object> pending = snapshot();
        send(owner, webUpdate(), null).andExpect(status().isConflict());
        send(owner, get("/dashboard/tools/" + tool + "/edit"), null).andExpect(status().isConflict());
        send(admin, get("/dashboard/tools/" + tool + "/edit"), null).andExpect(status().isConflict());
        // Invalid forms must not redisplay an editor for a non-DRAFT tool.
        send(owner, post("/dashboard/tools/" + tool).param("name", ""), null).andExpect(status().isConflict());
        assertEquals(pending, snapshot());
    }

    @ParameterizedTest @EnumSource(ToolStatus.class)
    void dashboardOffersEditorOnlyForDraft(ToolStatus state) throws Exception {
        setState(state);
        String page = send(owner, get("/dashboard/tools"), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(state == ToolStatus.DRAFT, page.contains("href=\"/dashboard/tools/" + tool + "/edit\""));
    }

    @Test void oldDecisionCannotApproveEditedAndResubmittedMetadata() throws Exception {
        send(owner, post("/api/v1/tools/" + tool + "/submit"), null).andExpect(status().isOk());
        send(admin, get("/admin/tools"), null).andExpect(status().isOk());
        decision("reject", 6).andExpect(status().isOk());
        send(owner, put(api()), changedBody()).andExpect(status().isOk());
        send(owner, post("/api/v1/tools/" + tool + "/submit"), null).andExpect(status().isOk());
        Map<String, Object> candidate = snapshot();
        decision("approve", 6).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_REVIEW_REVISION"));
        assertEquals(candidate, snapshot());
        decision("approve", 7).andExpect(status().isOk());
        assertEquals("Changed", snapshot().get("name"));
        assertEquals("PUBLISHED", snapshot().get("status"));
        assertEquals(7L, snapshot().get("review_revision"));
    }

    Map<String, Object> snapshot() { return jdbc.queryForMap("select * from tools where id=?", tool); }
    void setState(ToolStatus state) { jdbc.update("update tools set status=? where id=?", state.name(), tool); }
    String api() { return "/api/v1/tools/" + tool; }
    Client client(String actor) { return switch (actor) { case "owner" -> owner; case "admin" -> admin; default -> other; }; }
    String changedBody() { return "{\"name\":\"Changed\",\"slug\":\"" + changedSlug
            + "\",\"shortDescription\":\"Changed short\",\"description\":\"Changed details\",\"categoryId\":"
            + replacementCategory + ",\"repositoryUrl\":\"https://example.invalid/changed\"}"; }
    MockHttpServletRequestBuilder webUpdate() { return post("/dashboard/tools/" + tool).param("name", "Changed")
            .param("slug", changedSlug).param("shortDescription", "Changed short").param("description", "Changed details")
            .param("categoryId", Long.toString(replacementCategory)).param("repositoryUrl", "https://example.invalid/changed"); }
    ResultActions decision(String action, long revision) throws Exception {
        return send(admin, post("/api/v1/admin/tools/" + tool + "/" + action), "{\"expectedReviewRevision\":" + revision + "}");
    }
    void assertChanged() {
        Map<String, Object> actual = snapshot();
        assertAll(() -> assertEquals("Changed", actual.get("name")), () -> assertEquals(changedSlug, actual.get("slug")),
                () -> assertEquals("Changed short", actual.get("short_description")), () -> assertEquals("Changed details", actual.get("description")),
                () -> assertEquals(replacementCategory, actual.get("category_id")), () -> assertEquals("https://example.invalid/changed", actual.get("repository_url")),
                () -> assertEquals(owner.id(), actual.get("owner_id")), () -> assertEquals("DRAFT", actual.get("status")),
                () -> assertEquals(17L, actual.get("view_count")), () -> assertEquals(5L, actual.get("review_revision")));
    }
    ResultActions send(Client client, MockHttpServletRequestBuilder request, String json) throws Exception {
        request.session(client.session()).header(client.header(), client.token());
        if (json != null) request.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(request);
    }
    Client registerAndLogin(boolean isAdmin) throws Exception {
        Client initial = csrf(new MockHttpSession(), -1);
        String email = UUID.randomUUID() + "@test.invalid", password = "MetadataTest123!";
        MvcResult registration = send(initial, post("/api/v1/auth/register"), "{\"email\":\"" + email
                + "\",\"password\":\"" + password + "\",\"displayName\":\"Metadata test\"}").andExpect(status().isCreated()).andReturn();
        long id = ((Number) JsonPath.read(registration.getResponse().getContentAsString(), "$.id")).longValue();
        if (isAdmin) { var user = users.findById(id).orElseThrow(); user.setRole(Role.ADMIN); users.saveAndFlush(user); }
        MvcResult login = send(initial, post("/api/v1/auth/login"), "{\"email\":\"" + email
                + "\",\"password\":\"" + password + "\"}").andExpect(status().isOk()).andReturn();
        return csrf((MockHttpSession) login.getRequest().getSession(false), id);
    }
    Client csrf(MockHttpSession session, long id) throws Exception {
        String json = mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Client(session, JsonPath.read(json, "$.headerName"), JsonPath.read(json, "$.token"), id);
    }
    record Client(MockHttpSession session, String header, String token, long id) {}
}
