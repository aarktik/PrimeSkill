package com.example.toolhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.toolhub.domain.enums.PublishingAction;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.dto.request.ToolVersionRequest;
import com.example.toolhub.exception.InvalidStateTransitionException;
import com.example.toolhub.security.CurrentActor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoleEPersistenceTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PublishingService publishingService;
    @Autowired private ToolVersionService versionService;

    @Test
    void draftVersionIsStoredAndSubmitPersistsState() {
        jdbc.update("insert into users(id,email,password_hash) values (9001,'role-e-test@example.com','test')");
        jdbc.update("insert into categories(id,name,slug) values (9002,'Role E Test','role-e-test')");
        jdbc.update("insert into tools(id,name,slug,short_description,description,category_id,owner_id) "
                + "values (9003,'Tool','role-e-tool','Short','Details',9002,9001)");
        CurrentActor owner = new CurrentActor(9001L, false);

        var version = versionService.create(9003L, new ToolVersionRequest("1.0.0", "First release"), owner);
        assertEquals("1.0.0", version.version());

        var submitted = publishingService.transition(9003L, PublishingAction.SUBMIT, owner);
        assertEquals(ToolStatus.PENDING, submitted.getStatus());
        entityManager.flush();
        assertEquals("PENDING", jdbc.queryForObject("select status from tools where id = 9003", String.class));
        assertEquals("1.0.0", jdbc.queryForObject(
                "select version from tool_versions where tool_id = 9003", String.class));

        assertThrows(InvalidStateTransitionException.class,
                () -> versionService.create(9003L, new ToolVersionRequest("1.1.0", null), owner));
    }
}
