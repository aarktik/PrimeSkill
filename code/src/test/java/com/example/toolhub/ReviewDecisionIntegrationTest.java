package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.toolhub.domain.entity.*;
import com.example.toolhub.domain.enums.*;
import com.example.toolhub.repository.*;
import com.example.toolhub.security.*;
import com.example.toolhub.service.PublishingService;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Actual filters, controllers and committed services; PG subclass reuses the HTTP contract. */
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:b1_decisions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewDecisionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ToolRepository tools;
    @Autowired PublishingService publishing;
    UserPrincipal owner,admin,other;
    long tool,category;

    @BeforeEach void fixture() {
        String suffix=UUID.randomUUID().toString();
        User o=users.saveAndFlush(new User("owner-"+suffix+"@test.invalid","test-only"));
        User a=new User("admin-"+suffix+"@test.invalid","test-only"); a.setRole(Role.ADMIN); a=users.saveAndFlush(a);
        User u=users.saveAndFlush(new User("other-"+suffix+"@test.invalid","test-only"));
        owner=new UserPrincipal(o);admin=new UserPrincipal(a);other=new UserPrincipal(u);
        Category c=categories.saveAndFlush(new Category(suffix,"b1-"+suffix,null));category=c.getId();
        tool=tools.saveAndFlush(new Tool(o.getId(),c,"Original","b1-tool-"+suffix,"Short","Details",null)).getId();
    }
    @AfterEach void cleanup() {
        jdbc.update("delete from tools where id=?",tool);
        jdbc.update("delete from categories where id=?",category);
        for(UserPrincipal p:new UserPrincipal[]{owner,admin,other}) if(p!=null)jdbc.update("delete from users where id=?",p.getId());
    }
    String api(String action){return "/api/v1/admin/tools/"+tool+"/"+action;}
    ResultActions decision(String action,String body) throws Exception {
        var request=post(api(action)).with(user(admin)).with(csrf());
        if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }
    void submit() throws Exception {
        mvc.perform(post("/api/v1/tools/"+tool+"/submit").with(user(owner)).with(csrf())).andExpect(status().isOk());
    }
    long revision(){return jdbc.queryForObject("select review_revision from tools where id=?",Long.class,tool);}
    String state(){return jdbc.queryForObject("select status from tools where id=?",String.class,tool);}

    @Test void submissionRevisionIsReturnedAndOldApprovalCannotPublishResubmission() throws Exception {
        mvc.perform(get("/api/v1/tools/"+tool).with(user(owner))).andExpect(jsonPath("$.reviewRevision").value(0));
        submit();assertEquals(1,revision());
        decision("reject","{\"expectedReviewRevision\":1}").andExpect(status().isOk());
        assertEquals(1,revision());submit();assertEquals(2,revision());
        for(String action:new String[]{"approve","reject"}) {
            decision(action,"{\"expectedReviewRevision\":1}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_REVIEW_REVISION"));
            assertEquals("PENDING",state());
        }
        decision("approve","{\"expectedReviewRevision\":2}").andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewRevision").value(2));
        decision("approve","{\"expectedReviewRevision\":2}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }
    @ParameterizedTest
    @ValueSource(strings={"", "{}", "{\"expectedReviewRevision\":null}","{\"expectedReviewRevision\":-1}",
        "{\"expectedReviewRevision\":1.0}","{\"expectedReviewRevision\":1.9}","{\"expectedReviewRevision\":\"1\"}",
        "{\"expectedReviewRevision\":true}","{\"expectedReviewRevision\":9223372036854775808}"})
    void malformedDecisionNeverPublishes(String body) throws Exception {
        submit();decision("approve",body.isEmpty()?null:body).andExpect(status().isBadRequest());assertEquals("PENDING",state());
    }
    @Test void securityChecksPrecedeDecisionAndForgedActorCannotElevate() throws Exception {
        submit();
        mvc.perform(post(api("approve")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedReviewRevision\":1}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(api("approve")).with(user(other)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedReviewRevision\":1,\"actorIsAdmin\":true}")).andExpect(status().isForbidden());
        mvc.perform(post(api("approve")).with(user(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"expectedReviewRevision\":1}"))
            .andExpect(status().isForbidden());assertEquals("PENDING",state());
    }
    @Test void legacyPendingAtZeroCanBeReviewedButIdOnlyCannot() throws Exception {
        jdbc.update("update tools set status='PENDING' where id=?",tool);
        decision("approve",null).andExpect(status().isBadRequest());
        decision("approve","{\"expectedReviewRevision\":0}").andExpect(status().isOk());
    }
    @Test void moderationFormCarriesRevisionAndRejectsStaleSubmission() throws Exception {
        submit();
        mvc.perform(get("/admin/tools").with(user(admin))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"expectedReviewRevision\" value=\"1\"")));
        decision("reject","{\"expectedReviewRevision\":1}").andExpect(status().isOk());submit();
        mvc.perform(post("/admin/tools/"+tool+"/approve").with(user(admin)).with(csrf()).param("expectedReviewRevision","1"))
            .andExpect(status().isConflict());assertEquals("PENDING",state());
        mvc.perform(post("/admin/tools/"+tool+"/approve").with(user(admin)).with(csrf()).param("expectedReviewRevision","2"))
            .andExpect(status().is3xxRedirection());assertEquals("PUBLISHED",state());
    }
    @Test void genericTransitionCannotBypassRequiredRevision() throws Exception {
        submit();
        assertThrows(IllegalArgumentException.class,()->publishing.transition(tool,PublishingAction.APPROVE,new CurrentActor(admin.getId(),true)));
        assertEquals("PENDING",state());
    }
    @Test void revisionOverflowDoesNotChangeStatus() throws Exception {
        jdbc.update("update tools set review_revision=? where id=?",Long.MAX_VALUE,tool);
        mvc.perform(post("/api/v1/tools/"+tool+"/submit").with(user(owner)).with(csrf())).andExpect(status().isConflict());
        assertEquals(Long.MAX_VALUE,revision());assertEquals("DRAFT",state());
    }
    @Test void invalidWebRevisionsNeverApprove() throws Exception {
        submit();
        for(String token:new String[]{"-1","1.0","1e0","wrong","9223372036854775808"}) {
            mvc.perform(post("/admin/tools/"+tool+"/approve").with(user(admin)).with(csrf()).param("expectedReviewRevision",token))
                .andExpect(status().isBadRequest());
            assertEquals("PENDING",state());
        }
        mvc.perform(post("/admin/tools/"+tool+"/approve").with(user(admin)).with(csrf())).andExpect(status().isBadRequest());
    }
    @Test void visibilityCountersAndReviewsDoNotInvalidateSubmissionToken() throws Exception {
        submit();jdbc.update("update tools set view_count=view_count+1 where id=?",tool);
        decision("approve","{\"expectedReviewRevision\":1}").andExpect(status().isOk());
        var saved=mvc.perform(post("/api/v1/tools/"+tool+"/reviews").with(user(other)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"Fine\"}"))
            .andExpect(status().isCreated()).andReturn();
        long id=((Number)com.jayway.jsonpath.JsonPath.read(saved.getResponse().getContentAsString(),"$.id")).longValue();
        mvc.perform(put("/api/v1/tools/"+tool+"/reviews/"+id).with(user(other)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4,\"comment\":\"Updated\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/tools/"+tool+"/reviews/"+id).with(user(other)).with(csrf())).andExpect(status().isNoContent());
        assertEquals(1,revision());assertEquals("PUBLISHED",state());
        mvc.perform(post("/api/v1/tools/"+tool+"/deprecate").with(user(owner)).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/tools/"+tool+"/restore").with(user(owner)).with(csrf())).andExpect(status().isOk());
        assertEquals(1,revision());submit();assertEquals(2,revision());
    }
}
