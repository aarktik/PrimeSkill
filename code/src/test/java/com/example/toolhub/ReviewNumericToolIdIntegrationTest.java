package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.dto.request.UpdateReviewRequest;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.ResourceNotFoundException;
import com.example.toolhub.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:numeric_tool_ids;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class ReviewNumericToolIdIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReviewService reviews;

    @BeforeEach void fixtures() {
        for (String table : new String[]{"reviews", "tool_versions", "tool_tags", "tools", "categories", "user_profiles", "users"}) {
            jdbc.update("delete from " + table);
        }
        jdbc.update("insert into users(id,email,password_hash) values (9001,'numeric-owner@example.test','unused'),(9002,'numeric-other@example.test','unused')");
        jdbc.update("insert into categories(id,name,slug) values (9001,'Numeric IDs','numeric-ids')");
        jdbc.update("insert into tools(id,name,slug,short_description,description,category_id,owner_id,status) values (9001,'Hidden','hidden','test','test',9001,9001,'DRAFT'),(9002,'Public','9001','test','test',9001,9002,'PUBLISHED')");
        jdbc.update("insert into reviews(id,user_id,tool_id,rating,comment) values (9001,9002,9001,4,'Private fixture')");
    }

    @Test void numericSlugCannotExposeReviewsOfAnotherDraft() {
        assertThrows(ResourceNotFoundException.class,
                () -> reviews.listForTool(9001L,null,false,PageRequest.of(0,10)));
    }

    @Test void numericSlugCannotMakeDraftReviewable() {
        assertThrows(CatalogConflictException.class,
                () -> reviews.create(9001L,new CreateReviewRequest((short)5,"test"),9001L,false));
    }

    @Test void numericSlugCannotMakeDeprecatedReviewEditable() {
        jdbc.update("update tools set status='DEPRECATED' where id=9001");
        assertThrows(CatalogConflictException.class,
                () -> reviews.update(9001L,9001L,new UpdateReviewRequest((short)3,"test"),9002L,true));
    }

    @Test void numericSlugCannotBypassSelfReviewRestriction() {
        jdbc.update("update tools set status='PUBLISHED' where id=9001");
        assertThrows(AccessDeniedException.class,
                () -> reviews.create(9001L,new CreateReviewRequest((short)5,"test"),9001L,false));
    }
}
