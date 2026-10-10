package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.toolhub.domain.entity.Category;
import com.example.toolhub.domain.entity.User;
import com.example.toolhub.domain.enums.Role;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.dto.request.TagRequest;
import com.example.toolhub.repository.*;
import com.example.toolhub.service.TagService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:ui_complete;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UiAcceptanceIntegrationTest extends UiAcceptanceContract {}

@Transactional
abstract class UiAcceptanceContract {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ToolRepository tools;
    @Autowired TagService tags;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory factory;

    @Test void profileNormalizesEmptyAvatarAndCannotChangeAccountPrivileges() throws Exception {
        var user = login(false);
        send(user,post("/profile").param("displayName","  Updated member  ").param("bio","  About me  ")
                .param("avatarUrl"," ").param("role","ADMIN").param("email","other@example.test"),null)
                .andExpect(redirectedUrl("/profile"));
        mvc.perform(get("/api/v1/users/me").session(user.session)).andExpect(jsonPath("$.displayName").value("Updated member"))
                .andExpect(jsonPath("$.bio").value("About me")).andExpect(jsonPath("$.avatarUrl").isEmpty())
                .andExpect(jsonPath("$.role").value("USER")).andExpect(jsonPath("$.email").value(user.email));
    }

    @Test void profileErrorsRetainValuesAndUseActualBounds() throws Exception {
        var user=login(false);
        send(user,post("/profile").param("displayName","x").param("bio","keep my biography").param("avatarUrl","ftp://image.test/avatar"),null)
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("profileRequest","displayName","avatarUrl"))
                .andExpect(content().string(containsString("keep my biography")))
                .andExpect(content().string(containsString("Use 2 to 100 characters.")))
                .andExpect(content().string(containsString("data-error-summary")));
    }

    @Test void newPagesProtectSessionsCsrfAndAdminRoles() throws Exception {
        mvc.perform(get("/profile").accept(MediaType.TEXT_HTML)).andExpect(redirectedUrl("/login?next=%2Fprofile"));
        var member=login(false);
        for(String path:new String[]{"/admin/categories","/admin/categories/new","/admin/tags","/admin/tags/new"})
            mvc.perform(get(path).session(member.session)).andExpect(status().isForbidden());
        mvc.perform(post("/profile").session(member.session).param("displayName","Invalid CSRF"))
                .andExpect(status().isForbidden());
        var admin=login(true);
        for(String path:new String[]{"/admin/categories","/admin/categories/new","/admin/tags","/admin/tags/new"})
            mvc.perform(get(path).session(admin.session)).andExpect(status().isOk());
    }

    @Test void referenceFormsRetainDuplicateValuesAndBlockReferencedDeletion() throws Exception {
        var admin=login(true);String key="reference-"+UUID.randomUUID();
        send(admin,post("/admin/categories").param("name",key).param("slug",key).param("description","Reference description"),null)
                .andExpect(redirectedUrl("/admin/categories"));
        send(admin,post("/admin/categories").param("name",key).param("slug",key).param("description","Preserve this description"),null)
                .andExpect(status().isOk()).andExpect(view().name("admin/reference-form"))
                .andExpect(content().string(containsString("Preserve this description")))
                .andExpect(content().string(containsString("already exists")));
        long category=categories.findAll().stream().filter(c->c.getSlug().equals(key)).findFirst().orElseThrow().getId();
        long tool=tool(admin,category);
        send(admin,post("/admin/categories/"+category+"/delete"),null).andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attribute("errorMessage",containsString("still used")));
        assertTrue(categories.existsById(category));
        var tag=tags.create(new TagRequest(key,key),true);tags.assignTag(tool,tag.id(),admin.id,true);
        send(admin,post("/admin/tags/"+tag.id()+"/delete"),null).andExpect(redirectedUrl("/admin/tags"))
                .andExpect(flash().attribute("errorMessage",containsString("still used")));
        assertEquals(1,tags.findTagsOfTool(tool,admin.id,true).size());
    }

    @Test void catalogValidationDoesNotUseToolLengthLimits() throws Exception {
        var admin=login(true);
        send(admin,post("/admin/tags").param("name","n".repeat(101)).param("slug","bad SLUG"),null)
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("referenceRequest","name","slug"))
                .andExpect(content().string(containsString("Use at most 100 characters.")))
                .andExpect(content().string(containsString("lowercase letters")));
    }

    @Test void handledNotFoundPreservesAnonymousUserAndAdminMenus() throws Exception {
        String missing="/tools/missing-"+UUID.randomUUID();
        mvc.perform(get(missing)).andExpect(status().isNotFound())
                .andExpect(model().attribute("signedIn",false)).andExpect(model().attribute("isAdmin",false))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(not(containsString("data-logout"))));
        var member=login(false);
        mvc.perform(get(missing).session(member.session)).andExpect(status().isNotFound())
                .andExpect(model().attribute("signedIn",true)).andExpect(model().attribute("isAdmin",false))
                .andExpect(content().string(containsString("data-logout")))
                .andExpect(content().string(not(containsString("href=\"/admin/tools\""))));
        var admin=login(true);
        mvc.perform(get(missing).session(admin.session)).andExpect(status().isNotFound())
                .andExpect(model().attribute("signedIn",true)).andExpect(model().attribute("isAdmin",true))
                .andExpect(content().string(containsString("data-logout")))
                .andExpect(content().string(containsString("href=\"/admin/tools\"")));
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"", "   "})
    void blankToolNameCreateUsesThaiAndRetainsAllOtherFields(String name) throws Exception {
        var owner=login(false);long category=categories.saveAndFlush(new Category("Validation", "validation-"+UUID.randomUUID(),null)).getId();
        long count=tools.count();String slug="keep-"+UUID.randomUUID();
        send(owner,post("/dashboard/tools").param("name",name).param("slug",slug)
                .param("shortDescription","Keep short description").param("description","Keep full description")
                .param("categoryId",String.valueOf(category)).param("repositoryUrl","https://example.test/keep"),null)
                .andExpect(status().isOk()).andExpect(view().name("tools/form"))
                .andExpect(model().attributeHasFieldErrors("toolRequest","name"))
                .andExpect(content().string(containsString("กรุณาระบุชื่อเครื่องมือ")))
                .andExpect(content().string(containsString(slug)))
                .andExpect(content().string(containsString("Keep short description")))
                .andExpect(content().string(containsString("Keep full description")))
                .andExpect(content().string(containsString("https://example.test/keep")));
        assertEquals(count,tools.count());
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"", "   "})
    void blankToolNameEditUsesThaiRetainsInputAndDoesNotSave(String name) throws Exception {
        var owner=login(false);long id=tool(owner);var existing=tools.findById(id).orElseThrow();
        send(owner,post("/dashboard/tools/"+id).param("name",name).param("slug",existing.getSlug())
                .param("shortDescription","Keep edit short description").param("description","Keep edit full description")
                .param("categoryId",String.valueOf(existing.getCategory().getId())).param("repositoryUrl","https://example.test/edit"),null)
                .andExpect(status().isOk()).andExpect(view().name("tools/form"))
                .andExpect(model().attributeHasFieldErrors("toolRequest","name"))
                .andExpect(content().string(containsString("กรุณาระบุชื่อเครื่องมือ")))
                .andExpect(content().string(containsString("Keep edit short description")))
                .andExpect(content().string(containsString("Keep edit full description")))
                .andExpect(content().string(containsString("https://example.test/edit")));
        assertEquals("UI tool",tools.findById(id).orElseThrow().getName());
    }

    @ParameterizedTest @EnumSource(ToolStatus.class)
    void tagFormsFollowDraftGuardForEveryStatus(ToolStatus state) throws Exception {
        var owner=login(false);var outsider=login(false);var admin=login(true);
        long tool=tool(owner);var entity=tools.findById(tool).orElseThrow();entity.changeStatus(state);tools.saveAndFlush(entity);
        String key="tag-"+UUID.randomUUID();long tag=tags.create(new TagRequest(key,key),true).id();
        mvc.perform(get("/dashboard/tools/"+tool+"/tags").session(owner.session)).andExpect(status().isOk());
        mvc.perform(get("/dashboard/tools/"+tool+"/tags").session(admin.session)).andExpect(status().isOk());
        var denied=send(outsider,post("/dashboard/tools/"+tool+"/tags/assign").param("tagId",String.valueOf(tag)),null);
        denied.andExpect(status().isForbidden());
        var result=send(owner,post("/dashboard/tools/"+tool+"/tags/assign").param("tagId",String.valueOf(tag)),null);
        if(state==ToolStatus.DRAFT) {
            result.andExpect(status().is3xxRedirection());assertEquals(1,tags.findTagsOfTool(tool,owner.id,false).size());
            send(owner,post("/dashboard/tools/"+tool+"/tags/"+tag+"/unassign"),null).andExpect(status().is3xxRedirection());
            assertTrue(tags.findTagsOfTool(tool,owner.id,false).isEmpty());
        } else { result.andExpect(status().isConflict());assertTrue(tags.findTagsOfTool(tool,owner.id,false).isEmpty()); }
    }

    @Test void toolSlugConflictsStayInEditorWithAllSubmittedValues() throws Exception {
        var owner=login(false);long tool=tool(owner);var existing=tools.findById(tool).orElseThrow();
        send(owner,post("/dashboard/tools").param("name","Keep my name").param("slug",existing.getSlug())
                .param("shortDescription","Keep my short description").param("description","Keep my details")
                .param("categoryId",String.valueOf(existing.getCategory().getId())),null)
                .andExpect(status().isOk()).andExpect(view().name("tools/form"))
                .andExpect(model().attributeHasFieldErrors("toolRequest","slug"))
                .andExpect(content().string(containsString("Keep my details")));
    }

    @Test void missingReviewTargetsReturn404InsteadOf500() throws Exception {
        var member=login(false);
        send(member,post("/tools/9223372036854775807/reviews").param("rating","5"),null).andExpect(status().isNotFound());
        send(member,post("/tools/9223372036854775807/reviews/9223372036854775807/delete"),null).andExpect(status().isNotFound());
    }

    @Test void reviewCreateValidationRetainsItsBindingResultForAccessibleErrors() throws Exception {
        var owner=login(false);var member=login(false);long tool=tool(owner);
        tools.findById(tool).orElseThrow().changeStatus(ToolStatus.PUBLISHED);tools.flush();
        send(member,post("/tools/"+tool+"/reviews").param("rating","9").param("comment","Keep this review attempt"),null)
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("createReviewRequest","rating"))
                .andExpect(content().string(containsString("data-error-summary")))
                .andExpect(content().string(containsString("new-rating-error")))
                .andExpect(content().string(containsString("Keep this review attempt")))
                .andExpect(content().string(containsString("allowed range")));
    }

    @Test void independentReviewEditorPreservesSecondPageErrorsAndValues() throws Exception {
        var owner=login(false);var author=login(false);long tool=tool(owner);
        tools.findById(tool).orElseThrow().changeStatus(ToolStatus.PUBLISHED);tools.flush();
        jdbc.update("insert into reviews(tool_id,user_id,rating,comment) values(?,?,?,?)",tool,author.id,2,"Old review");
        long review=jdbc.queryForObject("select id from reviews where tool_id=? and user_id=?",Long.class,tool,author.id);
        for(int i=0;i<22;i++) {
            long other=users.saveAndFlush(new User(UUID.randomUUID()+"@example.test","unused")).getId();
            jdbc.update("insert into reviews(tool_id,user_id,rating) values(?,?,?)",tool,other,4);
        }
        mvc.perform(get("/tools/"+tool).session(author.session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"your-review\"")))
                .andExpect(content().string(containsString("Old review")));
        send(author,post("/tools/"+tool+"/reviews/"+review).param("page","1").param("rating","9")
                .param("comment","Preserve this attempted edit"),null).andExpect(status().isOk())
                .andExpect(content().string(containsString("Preserve this attempted edit")))
                .andExpect(content().string(containsString("your-rating-error")))
                .andExpect(content().string(containsString("aria-invalid=\"true\"")));
        assertEquals("Old review",jdbc.queryForObject("select comment from reviews where id=?",String.class,review));
    }

    @Test void detailReviewDeleteKeepsToolContext() throws Exception {
        var owner=login(false);var author=login(false);var admin=login(true);long tool=tool(owner);
        tools.findById(tool).orElseThrow().changeStatus(ToolStatus.PUBLISHED);tools.flush();
        jdbc.update("insert into reviews(tool_id,user_id,rating) values(?,?,?)",tool,author.id,4);
        long review=jdbc.queryForObject("select id from reviews where tool_id=? and user_id=?",Long.class,tool,author.id);
        String slug=tools.findById(tool).orElseThrow().getSlug();
        send(admin,post("/tools/"+tool+"/reviews/"+review+"/delete"),null)
                .andExpect(redirectedUrl("/tools/"+slug+"?page=0#reviews-heading"));
    }

    @Test void adminDeprecationDoesNotGrantOwnerOnlyVersionOrRestoreRights() throws Exception {
        var owner=login(false);var admin=login(true);long tool=tool(owner);
        tools.findById(tool).orElseThrow().changeStatus(ToolStatus.PUBLISHED);tools.flush();
        send(admin,post("/dashboard/tools/"+tool+"/deprecate"),null).andExpect(status().is3xxRedirection());
        assertEquals(ToolStatus.DEPRECATED,tools.findById(tool).orElseThrow().getStatus());
        send(admin,post("/dashboard/tools/"+tool+"/restore"),null).andExpect(status().isForbidden());
        send(admin,post("/dashboard/tools/"+tool+"/versions").param("version","1.0"),null).andExpect(status().isForbidden());
    }

    @Test void readingTagsDoesNotFetchEachTagIndividually() throws Exception {
        var owner=login(false);long tool=tool(owner);
        for(int i=0;i<20;i++) {String key="query-"+UUID.randomUUID();var tag=tags.create(new TagRequest(key,key),true);tags.assignTag(tool,tag.id(),owner.id,false);}
        em.flush();em.clear();var stats=factory.unwrap(SessionFactory.class).getStatistics();boolean previous=stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);stats.clear();
        try { assertEquals(20,tags.findTagsOfTool(tool,owner.id,false).size());assertTrue(stats.getPrepareStatementCount()<=2);assertEquals(0,stats.getEntityFetchCount()); }
        finally { stats.setStatisticsEnabled(previous); }
    }

    private Client login(boolean admin) throws Exception {
        var client=csrf(new MockHttpSession(),null,0L);String email=UUID.randomUUID()+"@example.test";
        send(client,post("/api/v1/auth/register"),"{\"email\":\""+email+"\",\"password\":\"UiTest123!\",\"displayName\":\"UI member\"}").andExpect(status().isCreated());
        var user=users.findByEmail(email).orElseThrow();if(admin){user.setRole(Role.ADMIN);users.saveAndFlush(user);}
        var login=send(client,post("/api/v1/auth/login"),"{\"email\":\""+email+"\",\"password\":\"UiTest123!\"}").andExpect(status().isOk()).andReturn();
        return csrf((MockHttpSession)login.getRequest().getSession(false),email,user.getId());
    }
    private Client csrf(MockHttpSession session,String email,long id) throws Exception {
        String json=mvc.perform(get("/api/v1/auth/csrf").session(session)).andReturn().getResponse().getContentAsString();
        return new Client(session,JsonPath.read(json,"$.headerName"),JsonPath.read(json,"$.token"),email,id);
    }
    private long tool(Client owner) throws Exception {
        String key="ui-"+UUID.randomUUID();return tool(owner,categories.saveAndFlush(new Category(key,key,null)).getId());
    }
    private long tool(Client owner,long category) throws Exception {
        String key="ui-"+UUID.randomUUID();String json="{\"name\":\"UI tool\",\"slug\":\""+key+"\",\"shortDescription\":\"Short\",\"description\":\"Details\",\"categoryId\":"+category+"}";
        return ((Number)JsonPath.read(send(owner,post("/api/v1/tools"),json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),"$.id")).longValue();
    }
    private ResultActions send(Client client,MockHttpServletRequestBuilder request,String json) throws Exception {
        request.session(client.session).header(client.header,client.token);if(json!=null)request.contentType(MediaType.APPLICATION_JSON).content(json);return mvc.perform(request);
    }
    private record Client(MockHttpSession session,String header,String token,String email,long id) {}
}
