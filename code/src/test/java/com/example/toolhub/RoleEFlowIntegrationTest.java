package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.toolhub.domain.entity.Category;
import com.example.toolhub.domain.enums.Role;
import com.example.toolhub.repository.CategoryRepository;
import com.example.toolhub.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Real controllers, security filters, services and repositories; isolated H2 database. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:role_e_flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleEFlowIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CategoryRepository categories;
    @Autowired private UserRepository users;

    @AfterEach
    void cleanIsolatedDatabase() {
        // This class has its own in-memory database. Requests commit their real service transactions.
        for (String table : new String[]{"tool_versions", "tools", "categories", "user_profiles", "users"}) {
            jdbc.update("delete from " + table);
        }
    }

    @Test
    void ownerCanPublishThenWithdrawAndRestoreWithRealSessions() throws Exception {
        Client owner = registerAndLogin(false);
        Client admin = registerAndLogin(true);
        long tool = createTool(owner);
        String versions = "/api/v1/tools/" + tool + "/versions";
        long version = id(send(owner, post(versions), "{\"version\":\"1.0\",\"releaseNotes\":\"Initial\"}")
                .andExpect(status().isCreated()).andReturn());
        send(owner, put(versions + "/" + version), "{\"version\":\"1.1\",\"releaseNotes\":\"Updated\"}")
                .andExpect(status().isOk());
        mvc.perform(get(versions)).andExpect(status().isNotFound());
        send(owner, post("/api/v1/tools/" + tool + "/submit"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        send(owner, put(versions + "/" + version), "{\"version\":\"2.0\"}")
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/admin/tools/pending").session(admin.session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(tool));
        send(admin, post("/api/v1/admin/tools/" + tool + "/reject"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        send(owner, post("/api/v1/tools/" + tool + "/submit"), null).andExpect(status().isOk());
        send(admin, post("/api/v1/admin/tools/" + tool + "/approve"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get(versions)).andExpect(status().isOk()).andExpect(jsonPath("$[0].version").value("1.1"));
        mvc.perform(get(versions + "/" + version)).andExpect(status().isOk());
        mvc.perform(get("/tools/" + tool + "/versions")).andExpect(status().isOk())
                .andExpect(view().name("versions/public"));
        send(owner, delete(versions + "/" + version), null).andExpect(status().isConflict());
        send(owner, post("/api/v1/tools/" + tool + "/deprecate"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DEPRECATED"));
        mvc.perform(get(versions)).andExpect(status().isNotFound());
        send(owner, post("/api/v1/tools/" + tool + "/restore"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        send(owner, delete(versions + "/" + version), null).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("select count(*) from tool_versions where tool_id = ?", Integer.class, tool));
    }

    @Test
    void securityRejectsMissingCsrfOtherOwnersAndUserApproval() throws Exception {
        Client owner = registerAndLogin(false);
        Client other = registerAndLogin(false);
        long tool = createTool(owner);
        String versions = "/api/v1/tools/" + tool + "/versions";
        String body = "{\"version\":\"1.0\"}";
        mvc.perform(post(versions).session(owner.session).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(versions).session(owner.session).header(owner.header, "invalid")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        send(other, post(versions), body).andExpect(status().isForbidden());
        mvc.perform(get(versions).session(other.session)).andExpect(status().isNotFound());
        long version = id(send(owner, post(versions), body).andExpect(status().isCreated()).andReturn());
        send(other, put(versions + "/" + version), body).andExpect(status().isForbidden());
        send(other, delete(versions + "/" + version), null).andExpect(status().isForbidden());
        send(owner, post(versions), body).andExpect(status().isConflict());
        long anotherTool = createTool(owner);
        send(owner, put("/api/v1/tools/" + anotherTool + "/versions/" + version), body)
                .andExpect(status().isNotFound());
        send(owner, post("/api/v1/tools/" + tool + "/submit"), null).andExpect(status().isOk());
        send(owner, post("/api/v1/admin/tools/" + tool + "/approve"), null).andExpect(status().isForbidden());
        mvc.perform(get("/admin/tools").session(owner.session)).andExpect(status().isForbidden());
        send(owner, post("/api/v1/tools/" + tool + "/submit"), null).andExpect(status().isConflict());
        send(owner, post("/api/v1/auth/logout"), null).andExpect(status().isNoContent());
        mvc.perform(get("/dashboard/tools/" + tool + "/versions")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicPageAssetsAndHealthAreAccessibleWithoutLogin() throws Exception {
        mvc.perform(get("/css/role-e.css")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    // These cases deliberately use committed requests and assert the persisted state after rejection.
    @org.junit.jupiter.params.ParameterizedTest(name = "transition {0} / {1} -> {2}")
    @org.junit.jupiter.params.provider.CsvSource({
        "DRAFT,submit,PENDING", "DRAFT,approve,-", "DRAFT,reject,-", "DRAFT,deprecate,-", "DRAFT,restore,-",
        "PENDING,submit,-", "PENDING,approve,PUBLISHED", "PENDING,reject,DRAFT", "PENDING,deprecate,-", "PENDING,restore,-",
        "PUBLISHED,submit,-", "PUBLISHED,approve,-", "PUBLISHED,reject,-", "PUBLISHED,deprecate,DEPRECATED", "PUBLISHED,restore,-",
        "DEPRECATED,submit,-", "DEPRECATED,approve,-", "DEPRECATED,reject,-", "DEPRECATED,deprecate,-", "DEPRECATED,restore,DRAFT"
    })
    void everyTransitionHasCorrectHttpResultAndPersistedState(String initial, String action, String next) throws Exception {
        Client owner = registerAndLogin(false);
        Client admin = registerAndLogin(true);
        long tool = createTool(owner);
        jdbc.update("update tools set status = ? where id = ?", initial, tool);
        boolean moderation = action.equals("approve") || action.equals("reject");
        String path = "/api/v1/" + (moderation ? "admin/" : "") + "tools/" + tool + "/" + action;
        send(moderation ? admin : owner, post(path), null)
                .andExpect(status().is(next.equals("-") ? 409 : 200));
        assertEquals(next.equals("-") ? initial : next,
                jdbc.queryForObject("select status from tools where id = ?", String.class, tool));
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "visibility and immutability in {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"DRAFT", "PENDING", "PUBLISHED", "DEPRECATED"})
    void visibilityAndMutationRulesApplyInEveryState(String state) throws Exception {
        Client owner = registerAndLogin(false);
        Client other = registerAndLogin(false);
        Client admin = registerAndLogin(true);
        long tool = createTool(owner);
        String url = "/api/v1/tools/" + tool + "/versions";
        long version = id(send(owner, post(url), "{\"version\":\"original\"}").andExpect(status().isCreated()).andReturn());
        jdbc.update("update tools set status = ? where id = ?", state, tool);
        for (String read : new String[]{url, url + "/" + version, "/tools/" + tool + "/versions"}) {
            mvc.perform(get(read)).andExpect(status().is(state.equals("PUBLISHED") ? 200 : 404));
            mvc.perform(get(read).session(other.session)).andExpect(status().is(state.equals("PUBLISHED") ? 200 : 404));
            mvc.perform(get(read).session(owner.session)).andExpect(status().isOk());
            mvc.perform(get(read).session(admin.session)).andExpect(status().isOk());
        }
        // Even an administrator may not edit someone else's versions.
        for (Client denied : new Client[]{other, admin}) {
            send(denied, post(url), "{\"version\":\"new\"}").andExpect(status().isForbidden());
            send(denied, put(url + "/" + version), "{\"version\":\"changed\"}").andExpect(status().isForbidden());
            send(denied, delete(url + "/" + version), null).andExpect(status().isForbidden());
        }
        if (!state.equals("DRAFT")) {
            send(owner, post(url), "{\"version\":\"new\"}").andExpect(status().isConflict());
            send(owner, put(url + "/" + version), "{\"version\":\"changed\"}").andExpect(status().isConflict());
            send(owner, delete(url + "/" + version), null).andExpect(status().isConflict());
        }
        assertEquals("original", jdbc.queryForObject("select version from tool_versions where id = ?", String.class, version));
        assertEquals(1, jdbc.queryForObject("select count(*) from tool_versions where tool_id = ?", Integer.class, tool));
    }

    static java.util.stream.Stream<String> invalidVersionBodies() {
        return java.util.stream.Stream.of("{}", "{\"version\":null}", "{\"version\":\"\"}",
                "{\"version\":\"   \"}", "{\"version\":\"\\t\\n\"}",
                "{\"version\":\"" + "x".repeat(101) + "\"}",
                "{\"version\":\"1\",\"releaseNotes\":\"" + "x".repeat(2001) + "\"}",
                "{", "null", "[]", "{\"version\":{}}", "");
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "invalid payload #{index}")
    @org.junit.jupiter.params.provider.MethodSource("invalidVersionBodies")
    void invalidCreateAndUpdateNeverPersist(String body) throws Exception {
        Client owner = registerAndLogin(false);
        long tool = createTool(owner);
        String url = "/api/v1/tools/" + tool + "/versions";
        long version = id(send(owner, post(url), "{\"version\":\"original\",\"releaseNotes\":\"keep\"}")
                .andExpect(status().isCreated()).andReturn());
        send(owner, post(url), body).andExpect(status().isBadRequest());
        send(owner, put(url + "/" + version), body).andExpect(status().isBadRequest());
        assertEquals(1, jdbc.queryForObject("select count(*) from tool_versions where tool_id = ?", Integer.class, tool));
        assertEquals("original", jdbc.queryForObject("select version from tool_versions where id = ?", String.class, version));
        assertEquals("keep", jdbc.queryForObject("select release_notes from tool_versions where id = ?", String.class, version));
    }

    @Test
    void boundariesNormalizationDuplicateRollbackAndOrdering() throws Exception {
        Client owner = registerAndLogin(false);
        long tool = createTool(owner);
        String url = "/api/v1/tools/" + tool + "/versions";
        long first = id(send(owner, post(url), "{\"version\":\"  1.0  \",\"releaseNotes\":\"keep\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value("1.0")).andReturn());
        long second = id(send(owner, post(url), "{\"version\":\"" + "v".repeat(100)
                + "\",\"releaseNotes\":\"" + "n".repeat(2000) + "\"}").andExpect(status().isCreated()).andReturn());
        send(owner, post(url), "{\"version\":\" 1.0 \"}").andExpect(status().isConflict());
        send(owner, put(url + "/" + second), "{\"version\":\" 1.0 \",\"releaseNotes\":\"lost\"}")
                .andExpect(status().isConflict());
        assertEquals("n".repeat(2000), jdbc.queryForObject("select release_notes from tool_versions where id = ?", String.class, second));
        send(owner, put(url + "/" + first), "{\"version\":\"1.0\"}").andExpect(status().isOk());
        mvc.perform(get(url).session(owner.session)).andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first));
        long another = createTool(owner);
        send(owner, post("/api/v1/tools/" + another + "/versions"), "{\"version\":\"1.0\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void wrongToolAndMissingIdsCannotReadUpdateOrDeleteVersions() throws Exception {
        Client owner = registerAndLogin(false);
        long tool = createTool(owner);
        long another = createTool(owner);
        String url = "/api/v1/tools/" + tool + "/versions";
        long version = id(send(owner, post(url), "{\"version\":\"keep\"}").andExpect(status().isCreated()).andReturn());
        for (String path : new String[]{"/api/v1/tools/" + another + "/versions/" + version,
                url + "/9223372036854775807", "/api/v1/tools/9223372036854775807/versions/" + version}) {
            mvc.perform(get(path).session(owner.session)).andExpect(status().isNotFound());
            send(owner, put(path), "{\"version\":\"changed\"}").andExpect(status().isNotFound());
            send(owner, delete(path), null).andExpect(status().isNotFound());
        }
        mvc.perform(get(url + "/not-a-number").session(owner.session)).andExpect(status().isBadRequest());
        assertEquals("keep", jdbc.queryForObject("select version from tool_versions where id = ?", String.class, version));
    }

    @Test
    void webFormsValidatePersistAndEscapeUntrustedText() throws Exception {
        Client owner = registerAndLogin(false);
        Client admin = registerAndLogin(true);
        long tool = createTool(owner);
        String web = "/dashboard/tools/" + tool + "/versions";
        mvc.perform(get(web + "/new").session(owner.session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
        send(owner, post(web).param("version", " "), null).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("versionRequest", "version"));
        assertEquals(0, jdbc.queryForObject("select count(*) from tool_versions where tool_id = ?", Integer.class, tool));
        String script = "<script>alert('x')</script>";
        send(owner, post(web).param("version", "1.0").param("releaseNotes", script), null)
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(web));
        long version = jdbc.queryForObject("select id from tool_versions where tool_id = ?", Long.class, tool);
        mvc.perform(get(web + "/" + version + "/edit").session(owner.session)).andExpect(status().isOk());
        send(owner, post(web + "/" + version).param("version", "2.0").param("releaseNotes", script), null)
                .andExpect(status().is3xxRedirection());
        send(owner, post("/dashboard/tools/" + tool + "/submit"), null).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/tools").session(admin.session)).andExpect(status().isOk());
        send(admin, post("/admin/tools/" + tool + "/approve"), null).andExpect(status().is3xxRedirection());
        mvc.perform(get("/tools/" + tool + "/versions")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(script))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
        send(owner, post(web + "/" + version + "/delete"), null).andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("select count(*) from tool_versions where tool_id = ?", Integer.class, tool));
    }

    @Test
    void csrfTokensFromAnotherSessionAndAnonymousWritesAreRejected() throws Exception {
        Client owner = registerAndLogin(false);
        Client stranger = csrf(new MockHttpSession());
        long tool = createTool(owner);
        String url = "/api/v1/tools/" + tool + "/versions";
        mvc.perform(post(url).session(owner.session).header(stranger.header, stranger.token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"1\"}"))
                .andExpect(status().isForbidden());
        send(stranger, post(url), "{\"version\":\"1\"}").andExpect(status().isUnauthorized());
        send(stranger, post("/api/v1/tools/" + tool + "/submit"), null).andExpect(status().isUnauthorized());
        assertEquals(0, jdbc.queryForObject("select count(*) from tool_versions where tool_id = ?", Integer.class, tool));
        assertEquals("DRAFT", jdbc.queryForObject("select status from tools where id = ?", String.class, tool));
    }

    @Test
    void simultaneousDuplicateCreatesAndSubmissionsHaveOnlyOneWinner() throws Exception {
        Client owner = registerAndLogin(false);
        long tool = createTool(owner);
        String url = "/api/v1/tools/" + tool + "/versions";
        assertEquals(java.util.List.of(201, 409), race(
                () -> send(owner, post(url), "{\"version\":\"same\"}").andReturn().getResponse().getStatus(),
                () -> send(owner, post(url), "{\"version\":\"same\"}").andReturn().getResponse().getStatus()));
        assertEquals(1, jdbc.queryForObject("select count(*) from tool_versions where tool_id = ?", Integer.class, tool));
        String submit = "/api/v1/tools/" + tool + "/submit";
        assertEquals(java.util.List.of(200, 409), race(
                () -> send(owner, post(submit), null).andReturn().getResponse().getStatus(),
                () -> send(owner, post(submit), null).andReturn().getResponse().getStatus()));
        assertEquals("PENDING", jdbc.queryForObject("select status from tools where id = ?", String.class, tool));
    }

    private java.util.List<Integer> race(java.util.concurrent.Callable<Integer> first,
                                         java.util.concurrent.Callable<Integer> second) throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var gate = new java.util.concurrent.CyclicBarrier(2);
        try {
            var a = pool.submit(() -> { gate.await(5, java.util.concurrent.TimeUnit.SECONDS); return first.call(); });
            var b = pool.submit(() -> { gate.await(5, java.util.concurrent.TimeUnit.SECONDS); return second.call(); });
            return java.util.stream.Stream.of(a.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    b.get(15, java.util.concurrent.TimeUnit.SECONDS)).sorted().toList();
        } finally {
            pool.shutdownNow();
            org.junit.jupiter.api.Assertions.assertTrue(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "DRAFT,submit", "PENDING,approve", "PENDING,reject", "PUBLISHED,deprecate", "DEPRECATED,restore"
    })
    void unrelatedUserCannotPerformAnyPublishingAction(String initial, String action) throws Exception {
        Client owner = registerAndLogin(false);
        Client other = registerAndLogin(false);
        long tool = createTool(owner);
        jdbc.update("update tools set status = ? where id = ?", initial, tool);
        boolean moderation = action.equals("approve") || action.equals("reject");
        send(other, post("/api/v1/" + (moderation ? "admin/" : "") + "tools/" + tool + "/" + action), null)
                .andExpect(status().isForbidden());
        // Dashboard hides private tools as 404 and denies mutation of visible tools as 403.
        send(other, post((moderation ? "/admin/tools/" : "/dashboard/tools/") + tool + "/" + action), null)
                .andExpect(status().is(moderation || initial.equals("PUBLISHED") ? 403 : 404));
        assertEquals(initial, jdbc.queryForObject("select status from tools where id = ?", String.class, tool));
    }

    @Test
    void moderationQueueFiltersAndPaginatesPersistedTools() throws Exception {
        Client owner = registerAndLogin(false);
        Client admin = registerAndLogin(true);
        long draft = createTool(owner);
        long pending1 = createTool(owner);
        long pending2 = createTool(owner);
        long published = createTool(owner);
        jdbc.update("update tools set status = 'PENDING' where id in (?, ?)", pending1, pending2);
        jdbc.update("update tools set status = 'PUBLISHED' where id = ?", published);
        String queue = "/api/v1/admin/tools/pending?size=1&sort=id,asc&page=";
        mvc.perform(get(queue + "0").session(admin.session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pending1));
        mvc.perform(get(queue + "1").session(admin.session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(pending2));
        mvc.perform(get(queue + "2").session(admin.session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get(queue + "0").session(owner.session)).andExpect(status().isForbidden());
        assertEquals("DRAFT", jdbc.queryForObject("select status from tools where id = ?", String.class, draft));
    }

    @Test
    void deletingToolCascadesVersionsWithoutAffectingOtherTools() throws Exception {
        Client owner = registerAndLogin(false);
        long tool = createTool(owner);
        long keep = createTool(owner);
        for (long id : new long[]{tool, keep}) {
            send(owner, post("/api/v1/tools/" + id + "/versions"), "{\"version\":\"1\"}")
                    .andExpect(status().isCreated());
        }
        send(owner, delete("/api/v1/tools/" + tool), null).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("select count(*) from tool_versions where tool_id = ?", Integer.class, tool));
        assertEquals(1, jdbc.queryForObject("select count(*) from tool_versions where tool_id = ?", Integer.class, keep));
    }

    @Test
    void submittingConcurrentlyWithVersionEditProducesASerializableOutcome() throws Exception {
        Client owner = registerAndLogin(false);
        long tool = createTool(owner);
        String url = "/api/v1/tools/" + tool;
        long version = id(send(owner, post(url + "/versions"), "{\"version\":\"before\"}")
                .andExpect(status().isCreated()).andReturn());
        var results = race(
                () -> send(owner, put(url + "/versions/" + version), "{\"version\":\"after\"}").andReturn().getResponse().getStatus(),
                () -> send(owner, post(url + "/submit"), null).andReturn().getResponse().getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(results.equals(java.util.List.of(200, 200))
                || results.equals(java.util.List.of(200, 409)), results.toString());
        assertEquals("PENDING", jdbc.queryForObject("select status from tools where id = ?", String.class, tool));
        assertEquals(results.contains(409) ? "before" : "after",
                jdbc.queryForObject("select version from tool_versions where id = ?", String.class, version));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"doesNotExist", "category.name", "ownerId", "name.length"})
    void unknownModerationSortFieldIsAClientError(String field) throws Exception {
        Client admin = registerAndLogin(true);
        mvc.perform(get("/api/v1/admin/tools/pending").param("sort", field + ",asc").session(admin.session))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"id", "name", "createdAt", "updatedAt"})
    void supportedModerationSortFieldsStillWork(String field) throws Exception {
        Client owner = registerAndLogin(false);
        Client admin = registerAndLogin(true);
        long tool = createTool(owner);
        send(owner, post("/api/v1/tools/" + tool + "/submit"), null).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/tools/pending").param("sort", field + ",desc").session(admin.session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(tool));
    }

    @Test
    void equalNamesHaveStablePageOrderAndMixedInvalidSortIsRejected() throws Exception {
        Client owner = registerAndLogin(false);
        Client admin = registerAndLogin(true);
        long first = createTool(owner);
        long second = createTool(owner);
        jdbc.update("update tools set status = 'PENDING' where id in (?, ?)", first, second);
        for (int page = 0; page < 2; page++) {
            mvc.perform(get("/api/v1/admin/tools/pending").session(admin.session)
                    .param("sort", "name,asc").param("size", "1").param("page", String.valueOf(page)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(page == 0 ? first : second));
        }
        mvc.perform(get("/api/v1/admin/tools/pending").session(admin.session)
                .param("sort", "name,asc", "invalid,desc")).andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedVersionPatchReturnsMethodNotAllowed() throws Exception {
        Client owner = registerAndLogin(false);
        long tool = createTool(owner);
        send(owner, patch("/api/v1/tools/" + tool + "/versions"), "{\"version\":\"1\"}")
                .andExpect(status().isMethodNotAllowed());
        assertEquals(0, jdbc.queryForObject("select count(*) from tool_versions where tool_id = ?", Integer.class, tool));
    }
    @Test
    void unsupportedRequestMediaTypeReturns415WithoutWritingData() throws Exception {
        Client owner = registerAndLogin(false);
        long tool = createTool(owner);
        mvc.perform(post("/api/v1/tools/" + tool + "/versions").session(owner.session)
                .header(owner.header, owner.token).contentType(MediaType.TEXT_PLAIN).content("version=1"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(header().string("Accept", org.hamcrest.Matchers.containsString("application/json")));
        assertEquals(0, jdbc.queryForObject("select count(*) from tool_versions where tool_id = ?", Integer.class, tool));
    }

    @Test
    void unsupportedResponseMediaTypeReturns406() throws Exception {
        Client owner = registerAndLogin(false);
        long tool = createTool(owner);
        mvc.perform(get("/api/v1/tools/" + tool + "/versions").session(owner.session)
                .accept("application/x-unsupported-format")).andExpect(status().isNotAcceptable());
    }

    @Test
    void csrfProtectsEveryPublishingActionAndWebForm() throws Exception {
        Client owner = registerAndLogin(false);
        Client admin = registerAndLogin(true);
        long tool = createTool(owner);
        for (String action : new String[]{"submit", "deprecate", "restore", "approve", "reject"}) {
            boolean moderation = action.equals("approve") || action.equals("reject");
            Client client = moderation ? admin : owner;
            String api = "/api/v1/" + (moderation ? "admin/" : "") + "tools/" + tool + "/" + action;
            String web = (moderation ? "/admin/tools/" : "/dashboard/tools/") + tool + "/" + action;
            mvc.perform(post(api).session(client.session)).andExpect(status().isForbidden());
            mvc.perform(post(web).session(client.session)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/dashboard/tools/" + tool + "/versions").session(owner.session).param("version", "1"))
                .andExpect(status().isForbidden());
        assertEquals("DRAFT", jdbc.queryForObject("select status from tools where id = ?", String.class, tool));
        assertEquals(0, jdbc.queryForObject("select count(*) from tool_versions where tool_id = ?", Integer.class, tool));
    }
    @Test
    void browserEntryPointsNavigationAndApiUnauthorizedStaySeparate() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("auth/login"));
        mvc.perform(get("/register")).andExpect(status().isOk()).andExpect(view().name("auth/register"));
        mvc.perform(get("/js/role-e.js")).andExpect(status().isOk());
        mvc.perform(get("/dashboard/tools").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/api/v1/admin/tools/pending").accept(MediaType.TEXT_HTML)).andExpect(status().isUnauthorized());
        Client owner = registerAndLogin(false);
        mvc.perform(get("/dashboard/tools").session(owner.session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("href=\"/admin/tools\""))));
        Client admin = registerAndLogin(true);
        mvc.perform(get("/admin/tools").session(admin.session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/admin/tools\"")));
    }

    @Test
    void toolDeleteFormWarnsAboutCascadeAndIncludesCsrf() throws Exception {
        Client owner = registerAndLogin(false);
        createTool(owner);
        mvc.perform(get("/dashboard/tools").session(owner.session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ลบเครื่องมือนี้และประวัติเวอร์ชันทั้งหมดหรือไม่?")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
    }

    @Test
    void duplicateWebVersionRetainsValuesAndShowsFieldError() throws Exception {
        Client owner = registerAndLogin(false);
        long tool = createTool(owner);
        String path = "/dashboard/tools/" + tool + "/versions";
        send(owner, post(path).param("version", "1"), null).andExpect(status().is3xxRedirection());
        send(owner, post(path).param("version", "1").param("releaseNotes", "keep my notes"), null)
                .andExpect(status().isOk()).andExpect(view().name("versions/form"))
                .andExpect(model().attributeHasFieldErrors("versionRequest", "version"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("keep my notes")));
        assertEquals(1, jdbc.queryForObject("select count(*) from tool_versions where tool_id = ?", Integer.class, tool));
    }
    private Client registerAndLogin(boolean admin) throws Exception {
        Client client = csrf(new MockHttpSession());
        String email = UUID.randomUUID() + "@example.test";
        String password = "RoleETest123!";
        send(client, post("/api/v1/auth/register"), "{\"email\":\"" + email
                + "\",\"password\":\"" + password + "\",\"displayName\":\"Role E Test\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"));
        // ADMIN provisioning is a test fixture only; registration must always create USER.
        if (admin) {
            var user = users.findByEmail(email).orElseThrow();
            user.setRole(Role.ADMIN);
            users.saveAndFlush(user);
        }
        MvcResult login = send(client, post("/api/v1/auth/login"), "{\"email\":\"" + email
                + "\",\"password\":\"" + password + "\"}").andExpect(status().isOk()).andReturn();
        return csrf((MockHttpSession) login.getRequest().getSession(false));
    }

    private Client csrf(MockHttpSession session) throws Exception {
        String json = mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Client(session, JsonPath.read(json, "$.headerName"), JsonPath.read(json, "$.token"));
    }

    private long createTool(Client owner) throws Exception {
        String slug = "flow-" + UUID.randomUUID();
        long category = categories.saveAndFlush(new Category(slug, slug, "Test category")).getId();
        return id(send(owner, post("/api/v1/tools"), "{\"name\":\"Flow tool\",\"slug\":\"" + slug
                + "\",\"shortDescription\":\"Short\",\"description\":\"Details\",\"categoryId\":" + category + "}")
                .andExpect(status().isCreated()).andReturn());
    }

    private ResultActions send(Client client, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.session(client.session).header(client.header, client.token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private long id(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private record Client(MockHttpSession session, String header, String token) {}
}
