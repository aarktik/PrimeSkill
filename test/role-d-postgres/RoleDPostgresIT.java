package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.toolhub.event.ReviewAuditListener;
import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.service.ReviewService;
import com.example.toolhub.service.ReviewSummaryService;
import com.example.toolhub.support.PostgresTestDatabaseGuard;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Test overlay for D only; do not compile this fixture in E without D's implementation. */
@SpringBootTest(properties = {"spring.datasource.url=${PRIMESKILL_TEST_DB_URL}",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = PostgresTestDatabaseGuard.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleDPostgresIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ReviewService reviews;
    @Autowired ReviewSummaryService summaries;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    private Client owner, author, other, admin;
    private long tool, category;
    private Logger auditLogger;
    private ListAppender<ILoggingEvent> audit;

    @BeforeAll
    void registerRealApiClients() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            assertTrue(connection.getCatalog().startsWith("primeskill_test_"));
        }
        owner = register(false);
        author = register(false);
        other = register(false);
        admin = register(true);
        auditLogger = (Logger) LoggerFactory.getLogger(ReviewAuditListener.class);
        audit = new ListAppender<>();
        audit.start();
        auditLogger.addAppender(audit);
    }

    @BeforeEach
    void fixtures() {
        jdbc.update("DELETE FROM reviews");
        jdbc.update("DELETE FROM tools");
        jdbc.update("DELETE FROM categories");
        category = jdbc.queryForObject("INSERT INTO categories(name,slug) VALUES('Review test','review-test') RETURNING id", Long.class);
        tool = tool("PUBLISHED");
        audit.list.clear();
    }

    @AfterAll
    void cleanDisposableDatabase() {
        if (auditLogger != null && audit != null) { auditLogger.detachAppender(audit); audit.stop(); }
        for (String table : List.of("reviews", "tools", "categories", "user_profiles", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @Test
    void apiCrudCommitsNormalizedDataAndRefreshesSummary() throws Exception {
        long review = create(author, 5, "  Original review  ");
        assertEquals("Original review", jdbc.queryForObject("SELECT comment FROM reviews WHERE id=?", String.class, review));
        assertEquals(5.0, summaries.summarizeByToolIds(List.of(tool)).get(tool).avgRating());
        send(author, put(path()+"/"+review), body(3, "  Edited  ")).andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").value("Edited")).andExpect(jsonPath("$.rating").value(3));
        assertEquals(3.0, summaries.summarizeByToolIds(List.of(tool)).get(tool).avgRating());
        mvc.perform(get(path())).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        send(author, delete(path()+"/"+review), null).andExpect(status().isNoContent());
        var empty = summaries.summarizeByToolIds(List.of(tool)).get(tool);
        assertEquals(0, empty.reviewCount());
        assertNull(empty.avgRating());
        assertEquals(0, count());
    }

    @Test
    void duplicateApiReviewIsConflictAndDatabaseUniqueIsAuthoritative() throws Exception {
        create(author, 5, "first");
        send(author, post(path()), body(4,"duplicate")).andExpect(status().isConflict());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO reviews(user_id,tool_id,rating) VALUES(?,?,4)", author.id, tool));
        assertEquals(1, count());
        assertEquals(1, audit.list.size());
    }

    @Test
    void ownerCannotSelfReviewAndOnlyAuthorCanEdit() throws Exception {
        send(owner, post(path()), body(5,"self")).andExpect(status().isForbidden());
        long review = create(author,5,"original");
        for (Client client : List.of(owner, other, admin)) {
            send(client, put(path()+"/"+review), body(1,"unauthorized")).andExpect(status().isForbidden());
        }
        send(other, delete(path()+"/"+review), null).andExpect(status().isForbidden());
        assertEquals("original", jdbc.queryForObject("SELECT comment FROM reviews WHERE id=?", String.class, review));
    }

    @ParameterizedTest
    @ValueSource(strings={"DRAFT","PENDING","DEPRECATED"})
    void hiddenToolVisibilityAndCreatePolicyAreEnforced(String state) throws Exception {
        jdbc.update("UPDATE tools SET status=? WHERE id=?", state, tool);
        mvc.perform(get(path())).andExpect(status().isNotFound());
        mvc.perform(get(path()).session(author.session)).andExpect(status().isNotFound());
        for (Client client : List.of(owner, admin)) {
            mvc.perform(get(path()).session(client.session)).andExpect(status().isOk());
            send(client,post(path()),body(5,"hidden")).andExpect(status().isConflict());
        }
        send(author,post(path()),body(5,"hidden")).andExpect(status().isNotFound());
        assertEquals(0,count());
    }

    @ParameterizedTest
    @ValueSource(strings={"author","admin"})
    void authorAndAdminCanDeleteAfterToolHidden(String actor) throws Exception {
        long review=create(author,4,"keep private");
        jdbc.update("UPDATE tools SET status='DEPRECATED' WHERE id=?",tool);
        send(author,put(path()+"/"+review),body(3,"edit hidden")).andExpect(status().isNotFound());
        mvc.perform(get("/my/reviews").session(author.session)).andExpect(status().isOk());
        send(actor.equals("author")?author:admin,delete(path()+"/"+review),null).andExpect(status().isNoContent());
        assertEquals(0,count());
    }

    @Test
    void wrongNestedReviewIdAndMissingToolDoNotChangeData() throws Exception {
        long review=create(author,5,"original");
        long secondTool=tool("PUBLISHED");
        String wrong="/api/v1/tools/"+secondTool+"/reviews/"+review;
        send(author,put(wrong),body(1,"wrong tool")).andExpect(status().isNotFound());
        send(admin,delete(wrong),null).andExpect(status().isNotFound());
        send(author,post("/api/v1/tools/9223372036854775807/reviews"),body(5,"missing")).andExpect(status().isNotFound());
        assertEquals(1,count());
    }

    @Test
    void sessionAndCsrfProtectCreateUpdateAndDelete() throws Exception {
        Client anonymous=csrf(new MockHttpSession(),-1);
        send(anonymous,post(path()),body(5,"anonymous")).andExpect(status().isUnauthorized());
        mvc.perform(post(path()).session(author.session).contentType(MediaType.APPLICATION_JSON).content(body(5,"missing csrf")))
                .andExpect(status().isForbidden());
        long review=create(author,5,"original");
        mvc.perform(put(path()+"/"+review).session(author.session).contentType(MediaType.APPLICATION_JSON).content(body(1,"missing")))
                .andExpect(status().isForbidden());
        mvc.perform(delete(path()+"/"+review).session(author.session).header(author.header,"wrong-token"))
                .andExpect(status().isForbidden());
        Client logoutClient=register(false);
        send(logoutClient,post("/api/v1/auth/logout"),null).andExpect(status().isNoContent());
        Client fresh=csrf(new MockHttpSession(),-1);
        send(fresh,delete(path()+"/"+review),null).andExpect(status().isUnauthorized());
        assertEquals("original",jdbc.queryForObject("SELECT comment FROM reviews WHERE id=?",String.class,review));
    }

    @ParameterizedTest
    @ValueSource(ints={0,6})
    void invalidRatingCannotWriteRows(int rating) throws Exception {
        send(author,post(path()),body(rating,"invalid")).andExpect(status().isBadRequest());
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "INSERT INTO reviews(user_id,tool_id,rating) VALUES(?,?,?)",author.id,tool,rating));
        assertEquals(0,count());
        assertTrue(audit.list.isEmpty());
    }

    @Test
    void nullRatingAndOversizedCommentReturnValidationError() throws Exception {
        send(author,post(path()),"{\"rating\":null,\"comment\":\"invalid\"}").andExpect(status().isBadRequest());
        send(author,post(path()),body(5,"a".repeat(2001))).andExpect(status().isBadRequest());
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "INSERT INTO reviews(user_id,tool_id,rating,comment) VALUES(?,?,5,?)",author.id,tool,"a".repeat(2001)));
        assertEquals(0,count());
    }

    @ParameterizedTest
    @ValueSource(ints={1,5})
    void acceptedRatingBoundariesArePersisted(int rating) throws Exception {
        long review=create(author,rating,"boundary");
        assertEquals(rating,jdbc.queryForObject("SELECT rating FROM reviews WHERE id=?",Integer.class,review));
    }

    @Test
    void forgedActorFieldsCannotOverrideSessionIdentity() throws Exception {
        String json="{\"rating\":5,\"comment\":\"forged actor\",\"userId\":"+admin.id+",\"toolId\":9223372036854775807}";
        int response=send(author,post(path()),json).andReturn().getResponse().getStatus();
        assertTrue(response==201 || response==400,"Unknown fields may be ignored or rejected, never trusted");
        assertEquals(response==201?1:0,count());
        if(response==201){
            assertEquals(author.id,jdbc.queryForObject("SELECT user_id FROM reviews",Long.class));
            assertEquals(tool,jdbc.queryForObject("SELECT tool_id FROM reviews",Long.class));
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"null","blank","boundary"})
    void optionalAndBoundaryCommentsFollowContract(String kind) throws Exception {
        String comment=switch(kind){case "null"->null;case "blank"->"   ";default->"ก".repeat(2000);};
        long review=create(author,5,comment);
        assertEquals(kind.equals("boundary")?comment:null,jdbc.queryForObject("SELECT comment FROM reviews WHERE id=?",String.class,review));
    }

    @Test
    void batchSummaryUsesOneQueryAndEmptyInputUsesNone() throws Exception {
        long unrated=tool("PUBLISHED");
        create(author,4,"first");
        create(other,5,"second");
        var statistics=entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var result=summaries.summarizeByToolIds(List.of(tool,unrated,tool));
        assertEquals(1,statistics.getPrepareStatementCount());
        assertEquals(4.5,result.get(tool).avgRating(),0.00001);
        assertEquals(2,result.get(tool).reviewCount());
        assertNull(result.get(unrated).avgRating());
        assertEquals(0,result.get(unrated).reviewCount());
        statistics.clear();
        assertTrue(summaries.summarizeByToolIds(List.of()).isEmpty());
        assertEquals(0,statistics.getPrepareStatementCount());
    }

    @Test
    void paginationIsStableAndResponseDoesNotExposeCredentials() throws Exception {
        long first=create(author,4,"first");
        long second=create(other,5,"second");
        jdbc.update("UPDATE reviews SET created_at=TIMESTAMPTZ '2026-01-01 00:00:00+00' WHERE tool_id=?",tool);
        String json=mvc.perform(get(path()).param("size","1").param("page","0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].id").value(Math.min(first,second)))
                .andReturn().getResponse().getContentAsString();
        assertFalse(json.contains("password")); assertFalse(json.contains("@example.test"));
        mvc.perform(get(path()).param("size","1").param("page","1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(Math.max(first,second)));
    }

    @ParameterizedTest
    @ValueSource(strings={"page=-1","size=0","size=101"})
    void invalidPaginationIsRejected(String input) throws Exception {
        String[] parts=input.split("=");
        mvc.perform(get(path()).param(parts[0],parts[1])).andExpect(status().isBadRequest());
    }

    @Test
    void databaseForeignKeysAndToolCascadeAreEnforced() throws Exception {
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "INSERT INTO reviews(user_id,tool_id,rating) VALUES(9223372036854775807,?,5)",tool));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "INSERT INTO reviews(user_id,tool_id,rating) VALUES(?,9223372036854775807,5)",author.id));
        create(author,5,"cascade");
        jdbc.update("DELETE FROM tools WHERE id=?",tool);
        assertEquals(0,count());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM users WHERE id=?",Integer.class,author.id));
    }

    @Test
    void deletingReviewerCascadesReviewsWithoutDeletingTool() throws Exception {
        Client disposable=register(false);
        create(disposable,5,"cascade user");
        jdbc.update("DELETE FROM users WHERE id=?",disposable.id);
        assertEquals(0,count());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM tools WHERE id=?",Integer.class,tool));
    }

    @Test
    void realAuditObserverRunsAfterCommitAndDoesNotLogComment() {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx->{
            var response=reviews.create(tool,new CreateReviewRequest((short)5,"sensitive-test-comment"),author.id,false);
            assertNotNull(response.id());
            assertTrue(audit.list.isEmpty(),"AFTER_COMMIT listener must not run inside transaction");
        });
        assertEquals(1,count());
        assertEquals(1,audit.list.size());
        String message=audit.list.get(0).getFormattedMessage();
        assertTrue(message.contains("reviewId="));
        assertTrue(message.contains("toolId="+tool));
        assertTrue(message.contains("userId="+author.id));
        assertFalse(message.contains("sensitive-test-comment"));
    }

    @Test
    void rollbackPreventsPersistenceAndAfterCommitAudit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx->{
            reviews.create(tool,new CreateReviewRequest((short)5,"rollback"),author.id,false);
            assertTrue(audit.list.isEmpty());
            tx.setRollbackOnly();
        });
        assertEquals(0,count());
        assertTrue(audit.list.isEmpty());
    }

    private long tool(String state) {
        return jdbc.queryForObject("INSERT INTO tools(owner_id,category_id,name,slug,short_description,description,status) "
                + "VALUES(?,?,'Review tool',?,'Short','Private fixture description',?) RETURNING id",Long.class,
                owner.id,category,"review-tool-"+UUID.randomUUID(),state);
    }
    private String path(){return "/api/v1/tools/"+tool+"/reviews";}
    private int count(){return jdbc.queryForObject("SELECT count(*) FROM reviews",Integer.class);}
    private String body(int rating,String comment){return "{\"rating\":"+rating+",\"comment\":"+(comment==null?"null":"\""+comment+"\"")+"}";}
    private long create(Client client,int rating,String comment)throws Exception{
        MvcResult result=send(client,post(path()),body(rating,comment)).andExpect(status().isCreated()).andReturn();
        long id=((Number)JsonPath.read(result.getResponse().getContentAsString(),"$.id")).longValue();
        assertEquals(path()+"/"+id,result.getResponse().getHeader("Location"));
        return id;
    }
    private Client register(boolean elevated)throws Exception{
        Client initial=csrf(new MockHttpSession(),-1);
        String email=UUID.randomUUID()+"@example.test";
        send(initial,post("/api/v1/auth/register"),"{\"email\":\""+email+"\",\"password\":\"ReviewOnly123!\",\"displayName\":\"PG Reviewer\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"));
        long id=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
        if(elevated)jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?",id);
        var login=send(initial,post("/api/v1/auth/login"),"{\"email\":\""+email+"\",\"password\":\"ReviewOnly123!\"}")
                .andExpect(status().isOk()).andReturn();
        return csrf((MockHttpSession)login.getRequest().getSession(false),id);
    }
    private Client csrf(MockHttpSession session,long id)throws Exception{
        String json=mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Client(session,JsonPath.read(json,"$.headerName"),JsonPath.read(json,"$.token"),id);
    }
    private ResultActions send(Client client,MockHttpServletRequestBuilder request,String body)throws Exception{
        request.session(client.session).header(client.header,client.token);
        if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }
    private record Client(MockHttpSession session,String header,String token,long id){}
}
