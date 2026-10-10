package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.toolhub.domain.entity.*;
import com.example.toolhub.domain.enums.*;
import com.example.toolhub.exception.*;
import com.example.toolhub.repository.*;
import com.example.toolhub.security.UserPrincipal;
import com.example.toolhub.service.TagService;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TagGuardIntegrationTest extends TagGuardContract {}

@Transactional
abstract class TagGuardContract {
    @Autowired TagService service;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ToolRepository tools;
    @Autowired TagRepository tags;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    User owner, admin, other;
    long tool, tag;

    @BeforeEach void seedTags() {
        String suffix = UUID.randomUUID().toString();
        owner = users.saveAndFlush(new User("owner-" + suffix + "@test.invalid", "unused"));
        other = users.saveAndFlush(new User("other-" + suffix + "@test.invalid", "unused"));
        admin = new User("admin-" + suffix + "@test.invalid", "unused");
        admin.setRole(Role.ADMIN); admin = users.saveAndFlush(admin);
        Category category = categories.saveAndFlush(new Category(suffix, suffix, null));
        tool = tools.saveAndFlush(new Tool(owner.getId(), category, "Original", suffix,
                "Short", "Description", null)).getId();
        tag = tags.saveAndFlush(new Tag("Tag " + suffix, "tag-" + suffix)).getId();
        jdbc.update("UPDATE tools SET view_count=17, review_revision=3 WHERE id=?", tool);
        em.clear();
    }

    static Stream<Arguments> matrix() {
        return Arrays.stream(ToolStatus.values()).flatMap(state -> Stream.of("owner", "admin", "other")
                .flatMap(actor -> Stream.of(true, false).map(assign -> Arguments.of(state, actor, assign))));
    }

    User actor(String name) { return switch (name) { case "owner" -> owner; case "admin" -> admin; default -> other; }; }
    long links() { return jdbc.queryForObject("SELECT COUNT(*) FROM tool_tags WHERE tool_id=? AND tag_id=?", Long.class, tool, tag); }
    Map<String, Object> snapshot() { return jdbc.queryForMap("SELECT * FROM tools WHERE id=?", tool); }
    void link() { jdbc.update("INSERT INTO tool_tags(tool_id,tag_id) VALUES(?,?)", tool, tag); }
    void state(ToolStatus state) { jdbc.update("UPDATE tools SET status=? WHERE id=?", state.name(), tool); em.clear(); }

    @ParameterizedTest(name = "{0}/{1}/assign={2}") @MethodSource("matrix")
    void persistedPermissionAndStateMatrix(ToolStatus state, String actor, boolean assign) {
        state(state);
        if (!assign) link();
        Map<String, Object> before = snapshot();
        User client = actor(actor);
        Runnable action = () -> {
            if (assign) service.assignTag(tool, tag, client.getId(), actor.equals("admin"));
            else service.unassignTag(tool, tag, client.getId(), actor.equals("admin"));
        };
        if (actor.equals("other")) assertThrows(AccessDeniedException.class, action::run);
        else if (state != ToolStatus.DRAFT) assertThrows(InvalidStateTransitionException.class, action::run);
        else action.run();
        em.flush();
        boolean allowed = !actor.equals("other") && state == ToolStatus.DRAFT;
        assertEquals(allowed ? (assign ? 1 : 0) : (assign ? 0 : 1), links());
        assertEquals(before, snapshot(), "Tag edits must not alter metadata/status/revision/views");
    }

    @ParameterizedTest @EnumSource(value = ToolStatus.class, names = {"PENDING", "PUBLISHED", "DEPRECATED"})
    void nonDraftNoOpIsRejectedBeforeDuplicateOrMissingAssociation(ToolStatus state) {
        state(state); link();
        assertThrows(InvalidStateTransitionException.class, () -> service.assignTag(tool, tag, owner.getId(), false));
        jdbc.update("DELETE FROM tool_tags WHERE tool_id=? AND tag_id=?", tool, tag);
        assertThrows(InvalidStateTransitionException.class, () -> service.unassignTag(tool, tag, admin.getId(), true));
        assertEquals(0, links());
    }

    @Test void draftDuplicateAndMissingAssociationKeepExistingErrorContract() {
        service.assignTag(tool, tag, owner.getId(), false); em.flush();
        assertThrows(CatalogConflictException.class, () -> service.assignTag(tool, tag, owner.getId(), false));
        service.unassignTag(tool, tag, owner.getId(), false); em.flush();
        assertThrows(ResourceNotFoundException.class, () -> service.unassignTag(tool, tag, owner.getId(), false));
        assertEquals(0, links());
    }

    @Test void referencedTagCannotBeDeletedAndUnusedTagCanBeDeleted() {
        link();
        assertThrows(CatalogConflictException.class, () -> service.delete(tag, true));
        assertEquals(1, links()); assertTrue(tags.existsById(tag));
        service.unassignTag(tool, tag, owner.getId(), false);
        service.delete(tag, true); em.flush();
        assertFalse(tags.existsById(tag)); assertEquals(0, links());
    }

    @Test void missingResourcesAndNonAdminDeleteDoNotWrite() {
        assertThrows(ResourceNotFoundException.class, () -> service.assignTag(Long.MAX_VALUE, tag, owner.getId(), false));
        assertThrows(ResourceNotFoundException.class, () -> service.assignTag(tool, Long.MAX_VALUE, owner.getId(), false));
        assertThrows(AccessDeniedException.class, () -> service.delete(tag, false));
        assertEquals(0, links()); assertTrue(tags.existsById(tag));
    }

    @ParameterizedTest @EnumSource(ToolStatus.class)
    void realApiEnforcesOwnerStateAndJsonErrors(ToolStatus state) throws Exception {
        state(state);
        String path = "/api/v1/tools/" + tool + "/tags/" + tag;
        int expected = state == ToolStatus.DRAFT ? 204 : 409;
        var result = mvc.perform(post(path).with(user(new UserPrincipal(owner))).with(csrf()))
                .andExpect(status().is(expected));
        em.flush();
        if (expected == 409) result.andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        if (expected == 204) assertEquals(1, links());
        mvc.perform(delete(path).with(user(new UserPrincipal(owner))).with(csrf())).andExpect(status().is(expected));
        em.flush();
        assertEquals(0, links());
    }

    @Test void realApiRejectsAnonymousNonOwnerAndMissingCsrf() throws Exception {
        String path = "/api/v1/tools/" + tool + "/tags/" + tag;
        mvc.perform(post(path).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(user(new UserPrincipal(other))).with(csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post(path).with(user(new UserPrincipal(owner)))).andExpect(status().isForbidden());
        assertEquals(0, links());
    }
}
