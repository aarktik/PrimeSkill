package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.*;
import com.example.toolhub.support.JdbcSessionTestInstances;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.*;
import java.sql.DriverManager;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSessionPostgresIT {
    JdbcSessionTestInstances servers;
    JdbcTemplate sql;
    final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    String a, b;
    String hash;

    @BeforeAll void setup() throws Exception {
        servers = new JdbcSessionTestInstances();
        a = servers.address(servers.a); b = servers.address(servers.b);
        sql = new JdbcTemplate(servers.b.getBean(DataSource.class));
        hash = servers.a.getBean(org.springframework.security.crypto.password.PasswordEncoder.class).encode("Session-test-Password1!");
        for (String role : new String[]{"USER", "ADMIN"})
            sql.update("insert into users(email,password_hash,role) values (?,?,?)", role.toLowerCase()+"@session.test", hash, role);
    }
    @AfterAll void shutdown() { if (servers != null) servers.close(); }

    HttpResponse<String> send(String host, String path, String cookie, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(host + path)).timeout(java.time.Duration.ofSeconds(20));
        if (path.startsWith("/v3/api-docs")) request.header("Accept", "application/json");
        else if (!path.startsWith("/api/")) request.header("Accept", "text/html");
        if (cookie != null) request.header("Cookie", cookie);
        if (token != null) request.header("X-CSRF-TOKEN", token);
        if (body == null) request.GET(); else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    String cookie(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream().filter(v -> v.startsWith("JSESSIONID=")).findFirst().orElseThrow().split(";",2)[0];
    }
    String token(HttpResponse<String> response) { return JsonPath.read(response.body(), "$.token"); }
    String login(String role) throws Exception {
        return loginEmail(role.toLowerCase()+"@session.test",role);
    }
    String loginEmail(String email, String role) throws Exception {
        var csrf = send(a,"/api/v1/auth/csrf",null,null,null);
        var logged = send(b,"/api/v1/auth/login",cookie(csrf),token(csrf),"{\"email\":\""+email+"\",\"password\":\"Session-test-Password1!\"}");
        assertEquals(200, logged.statusCode(), logged.body());
        assertEquals(role, JsonPath.read(logged.body(),"$.role"));
        assertNotEquals(cookie(csrf),cookie(logged));
        assertEquals(302,send(a,"/dashboard/tools",cookie(csrf),null,null).statusCode());
        return cookie(logged);
    }

    @Test void sessionSqlDraftExists() { assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("../doc/sql/drafts/S1__add_shared_http_sessions.sql"))); }
    @Test void longEmailStillLogsIn() throws Exception {
        String email="a".repeat(60)+"@"+"b".repeat(60)+".test";
        sql.update("insert into users(email,password_hash,role) values (?,?,'USER')",email,hash);
        var csrf=send(a,"/api/v1/auth/csrf",null,null,null);
        assertEquals(200,send(b,"/api/v1/auth/login",cookie(csrf),token(csrf),
                "{\"email\":\""+email+"\",\"password\":\"Session-test-Password1!\"}").statusCode());
    }
    @Test void csrfTravelsAcrossInstancesAndCookieContractIsPreserved() throws Exception {
        var csrf=send(a,"/api/v1/auth/csrf",null,null,null);
        String header=csrf.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(header.contains("HttpOnly")); assertTrue(header.contains("SameSite=Lax")); assertFalse(header.contains("Domain="));
        assertEquals(403,send(b,"/api/v1/auth/login",cookie(csrf),null,"{}").statusCode());
        assertEquals(403,send(b,"/api/v1/auth/login",cookie(csrf),"wrong","{}").statusCode());
        var other=send(b,"/api/v1/auth/csrf",null,null,null);
        assertEquals(403,send(b,"/api/v1/auth/login",cookie(csrf),token(other),"{}").statusCode());
        assertEquals(400,send(b,"/api/v1/auth/login",cookie(csrf),token(csrf),"{}").statusCode());
    }
    @Test void userAndAdminSurviveSerializationAndEnforceRoles() throws Exception {
        String user=login("USER"), admin=login("ADMIN");
        assertEquals(200,send(a,"/dashboard/tools",user,null,null).statusCode());
        assertEquals(403,send(a,"/admin/tools",user,null,null).statusCode());
        assertEquals(200,send(a,"/admin/tools",admin,null,null).statusCode());
        for(byte[] bytes:sql.query("select attribute_bytes from spring_session_attributes",(rs,n)->rs.getBytes(1)))
            assertFalse(new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1).contains(hash));
    }
    @Test void logoutInvalidatesOtherInstance() throws Exception {
        String session=login("USER");
        var csrf=send(a,"/api/v1/auth/csrf",session,null,null);
        assertEquals(204,send(b,"/api/v1/auth/logout",session,token(csrf),"{}").statusCode());
        assertEquals(302,send(a,"/dashboard/tools",session,null,null).statusCode());
    }
    @Test void instanceRestartKeepsLogin() throws Exception {
        String session=login("USER"); servers.a.close(); servers.a=servers.start(); a=servers.address(servers.a);
        assertEquals(200,send(a,"/dashboard/tools",session,null,null).statusCode());
    }
    @Test void expiredSessionsAreRefusedWithoutWaitingForCleanup() throws Exception {
        String session=login("USER");
        sql.update("update spring_session set last_access_time=0,expiry_time=0");
        assertEquals(302,send(b,"/dashboard/tools",session,null,null).statusCode());
    }
    @Test void parallelReadsCannotResurrectLoggedOutSession() throws Exception {
        String session=login("USER"); var csrf=send(a,"/api/v1/auth/csrf",session,null,null);
        var pool=Executors.newFixedThreadPool(4);
        try {
            var reads=new java.util.ArrayList<Future<Integer>>();
            for(int i=0;i<12;i++) reads.add(pool.submit(()->send(a,"/dashboard/tools",session,null,null).statusCode()));
            assertEquals(204,send(b,"/api/v1/auth/logout",session,token(csrf),"{}").statusCode());
            for(var read:reads) assertTrue(java.util.Set.of(200,302).contains(read.get()));
            assertEquals(302,send(a,"/dashboard/tools",session,null,null).statusCode());
        } finally { pool.shutdownNow(); }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test void staleSessionSaveAfterLogoutCannotRestoreAuthentication() throws Exception {
        String session=login("USER");
        String id=new String(java.util.Base64.getDecoder().decode(session.substring("JSESSIONID=".length())), java.nio.charset.StandardCharsets.UTF_8);
        org.springframework.session.SessionRepository repository=servers.a.getBean(org.springframework.session.jdbc.JdbcIndexedSessionRepository.class);
        org.springframework.session.Session stale=repository.findById(id);
        assertNotNull(stale);
        var csrf=send(a,"/api/v1/auth/csrf",session,null,null);
        assertEquals(204,send(b,"/api/v1/auth/logout",session,token(csrf),"{}").statusCode());
        stale.setAttribute("concurrent-request", "late-save");
        try { repository.save(stale); }
        catch (org.springframework.dao.DataIntegrityViolationException expected) { /* Deleted parent rejects late attribute insert. */ }
        assertNull(repository.findById(id));
        assertEquals(302,send(b,"/dashboard/tools",session,null,null).statusCode());
    }
    @Test void jdbcProfileUsesRepositoryAndThirtyMinuteTimeout() {
        assertTrue(servers.a.containsBean("springSessionRepositoryFilter"));
        var repository=servers.a.getBean(org.springframework.session.jdbc.JdbcIndexedSessionRepository.class);
        org.springframework.session.Session session=repository.createSession();
        assertEquals(java.time.Duration.ofMinutes(30),session.getMaxInactiveInterval());
    }
    @Test void sessionTablesAreNotExposedToPublicApiRoles() {
        for(String role: new String[]{"anon","authenticated"})
            for(String table:new String[]{"spring_session","spring_session_attributes"})
                for(String privilege:new String[]{"SELECT","INSERT","UPDATE","DELETE","TRUNCATE","REFERENCES","TRIGGER"})
                    assertFalse(sql.queryForObject("select has_table_privilege(?,?,?)",Boolean.class,role,"public."+table,privilege));
    }
    @Test void deploymentProfileSetsSecureCookieAndEnablesDocumentation() throws Exception {
        try(var deployment=servers.start(true)) {
            String host=servers.address(deployment);
            // Exercise first-use initialization, not just a successfully bound port.
            assertEquals(200,send(host,"/login",null,null,null).statusCode());
            assertEquals(200,send(host,"/tools",null,null,null).statusCode());
            assertEquals(302,send(host,"/dashboard/tools",null,null,null).statusCode());
            assertEquals(401,send(host,"/api/v1/auth/me",null,null,null).statusCode());
            assertEquals(403,send(host,"/api/v1/auth/login",null,null,"{}").statusCode());
            var csrf=send(host,"/api/v1/auth/csrf",null,null,null);
            assertEquals(200,csrf.statusCode());
            String header=csrf.headers().firstValue("Set-Cookie").orElseThrow();
            assertTrue(header.contains("Secure")); assertTrue(header.contains("HttpOnly"));
            assertTrue(header.contains("SameSite=Lax")); assertFalse(header.contains("Domain="));
            assertTrue(deployment.getEnvironment().getProperty("springdoc.api-docs.enabled",Boolean.class,false));
            assertTrue(deployment.getEnvironment().getProperty("springdoc.swagger-ui.enabled",Boolean.class,false));
            assertEquals(200,send(host,"/v3/api-docs",null,null,null).statusCode());
            assertEquals(200,send(host,"/swagger-ui/index.html",null,null,null).statusCode());
            var swaggerConfig=send(host,"/v3/api-docs/swagger-config",null,null,null);
            assertEquals(200,swaggerConfig.statusCode());
            assertTrue(JsonPath.<java.util.List<?>>read(swaggerConfig.body(),"$.supportedSubmitMethods").isEmpty());
            assertEquals("never",deployment.getEnvironment().getProperty("spring.sql.init.mode"));
        }
    }
    @Test void migrationRejectsInheritedTruncatePrivilegeAtomically() throws Exception {
        String name="primeskill_test_session_acl_"+java.util.UUID.randomUUID().toString().replace("-", "");
        try(var connection=DriverManager.getConnection(servers.url,servers.username,servers.password);
            var statement=connection.createStatement()) { statement.execute("CREATE DATABASE "+name); }
        String url=servers.url.substring(0,servers.url.lastIndexOf('/')+1)+name;
        try(var connection=DriverManager.getConnection(url,servers.username,servers.password);
            var statement=connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("CREATE ROLE session_api_parent");
            statement.execute("GRANT session_api_parent TO anon");
            statement.execute("ALTER DEFAULT PRIVILEGES GRANT TRUNCATE ON TABLES TO session_api_parent");
            String migration=java.nio.file.Files.readString(java.nio.file.Path.of("../doc/sql/drafts/S1__add_shared_http_sessions.sql"));
            try {
                var error=assertThrows(java.sql.SQLException.class,()->statement.execute(migration));
                assertEquals("P0001",error.getSQLState());
            } finally { connection.rollback(); }
            try(var result=statement.executeQuery("select to_regclass('public.spring_session') is null and to_regclass('public.spring_session_attributes') is null")) {
                result.next(); assertTrue(result.getBoolean(1));
            }
        }
    }
    @Test void ownerMemberAndAdminKeepDistinctToolPermissionsAcrossInstances() throws Exception {
        sql.update("insert into users(email,password_hash,role) values ('member@session.test',?,'USER')",hash);
        Long owner=sql.queryForObject("select id from users where email='user@session.test'",Long.class);
        Long category=sql.queryForObject("insert into categories(name,slug) values ('Session category','session-category') returning id",Long.class);
        Long tool=sql.queryForObject("insert into tools(name,slug,short_description,description,category_id,owner_id) values ('Session tool','session-tool','short','description',?,?) returning id",Long.class,category,owner);
        String ownerSession=login("USER"), memberSession=loginEmail("member@session.test","USER"), admin=login("ADMIN");
        String path="/dashboard/tools/"+tool+"/edit";
        assertEquals(200,send(a,path,ownerSession,null,null).statusCode());
        // Existing visibility contract hides another member's unpublished draft.
        assertEquals(404,send(a,path,memberSession,null,null).statusCode());
        assertEquals(200,send(a,path,admin,null,null).statusCode());
    }
}
