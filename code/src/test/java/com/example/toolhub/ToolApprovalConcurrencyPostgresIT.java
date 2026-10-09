package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.*;
import com.example.toolhub.domain.entity.*;
import com.example.toolhub.domain.enums.*;
import com.example.toolhub.dto.request.ToolVersionRequest;
import com.example.toolhub.exception.*;
import com.example.toolhub.repository.*;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.service.*;
import com.example.toolhub.support.PostgresTestDatabaseGuard;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties={"spring.datasource.url=${PRIMESKILL_TEST_DB_URL}","spring.jpa.open-in-view=false"})
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers=PostgresTestDatabaseGuard.class)
class ToolApprovalConcurrencyPostgresIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired PublishingService publishing;
    @Autowired ToolVersionService versions;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ToolRepository tools;
    long tool,category,user,version;
    ExecutorService workers;
    CurrentActor owner,admin;
    @BeforeEach void seed(){
        String suffix=UUID.randomUUID().toString();
        user=users.saveAndFlush(new User(suffix+"@test.invalid","test-only")).getId();
        Category c=categories.saveAndFlush(new Category(suffix,"b1-"+suffix,null));category=c.getId();
        tool=tools.saveAndFlush(new Tool(user,c,"Original","b1-"+suffix,"Short","Details",null)).getId();
        owner=new CurrentActor(user,false);admin=new CurrentActor(user,true);
        version=versions.create(tool,new ToolVersionRequest("original","keep"),owner).id();
        workers=Executors.newFixedThreadPool(2);
    }
    @AfterEach void clean()throws Exception{
        workers.shutdownNow();assertTrue(workers.awaitTermination(15,TimeUnit.SECONDS));
        jdbc.update("delete from tools where id=?",tool);jdbc.update("delete from categories where id=?",category);jdbc.update("delete from users where id=?",user);
    }
    void tx(Runnable action){var t=new TransactionTemplate(manager);t.setTimeout(20);t.executeWithoutResult(s->action.run());}
    void txExpectedRejection(Runnable action){var t=new TransactionTemplate(manager);t.setTimeout(20);t.executeWithoutResult(s->{try{action.run();}finally{s.setRollbackOnly();}});}
    long rev(){return jdbc.queryForObject("select review_revision from tools where id=?",Long.class,tool);}
    String state(){return jdbc.queryForObject("select status from tools where id=?",String.class,tool);}
    void mutate(String operation){switch(operation){
        case "create"->versions.create(tool,new ToolVersionRequest("new","changed"),owner);
        case "update"->versions.update(tool,version,new ToolVersionRequest("changed","changed"),owner);
        case "delete"->versions.delete(tool,version,owner);
        default->throw new IllegalArgumentException(operation);
    }}
    @ParameterizedTest @ValueSource(strings={"create","update","delete"})
    void staleDraftVersionWriterCannotBypassSubmittedState(String operation)throws Exception{
        txExpectedRejection(()->{
            Tool cached=em.find(Tool.class,tool);assertEquals(ToolStatus.DRAFT,cached.getStatus());
            try{workers.submit(()->publishing.transition(tool,PublishingAction.SUBMIT,owner)).get(15,TimeUnit.SECONDS);}
            catch(Exception ex){throw new AssertionError(ex);}
            assertEquals(ToolStatus.DRAFT,cached.getStatus(),"Must reproduce a stale managed entity");
            assertThrows(InvalidStateTransitionException.class,()->mutate(operation));
        });
        assertEquals("PENDING",state());assertEquals(1,rev());
        assertEquals("original",jdbc.queryForObject("select version from tool_versions where id=?",String.class,version));
        assertEquals(1,jdbc.queryForObject("select count(*) from tool_versions where tool_id=?",Integer.class,tool));
    }
    @Test void staleDecisionCannotApproveNewSubmission()throws Exception{
        publishing.transition(tool,PublishingAction.SUBMIT,owner);
        txExpectedRejection(()->{
            Tool cached=em.find(Tool.class,tool);
            assertEquals(1,cached.getReviewRevision());
            try{workers.submit(()->{
                publishing.decide(tool,PublishingAction.REJECT,1,admin);
                publishing.transition(tool,PublishingAction.SUBMIT,owner);
            }).get(15,TimeUnit.SECONDS);}catch(Exception ex){throw new AssertionError(ex);}
            assertEquals(1,cached.getReviewRevision());
            assertThrows(StaleReviewRevisionException.class,()->publishing.decide(tool,PublishingAction.APPROVE,1,admin));
        });
        assertEquals("PENDING",state());assertEquals(2,rev());
    }
    @ParameterizedTest @CsvSource({"version,create","submit,create","version,update","submit,update","version,delete","submit,delete"})
    void versionMutationAndSubmitUseSameLock(String first,String operation)throws Exception{
        boolean versionFirst=first.equals("version");
        raced(()->{if(versionFirst)mutate(operation);else publishing.transition(tool,PublishingAction.SUBMIT,owner);},
              ()->{if(versionFirst)publishing.transition(tool,PublishingAction.SUBMIT,owner);else mutate(operation);},
              false,versionFirst?null:InvalidStateTransitionException.class);
        assertEquals("PENDING",state());assertEquals(1,rev());
        int count=jdbc.queryForObject("select count(*) from tool_versions where tool_id=?",Integer.class,tool);
        assertEquals(versionFirst?(operation.equals("create")?2:operation.equals("delete")?0:1):1,count);
        if(!versionFirst || operation.equals("update"))assertEquals(versionFirst?"changed":"original",jdbc.queryForObject("select version from tool_versions where id=?",String.class,version));
    }
    @ParameterizedTest @ValueSource(strings={"APPROVE","REJECT"})
    void competingDecisionsSerializeAndOnlyFirstCommits(String action)throws Exception{
        publishing.transition(tool,PublishingAction.SUBMIT,owner);
        var first=PublishingAction.valueOf(action);var second=first==PublishingAction.APPROVE?PublishingAction.REJECT:PublishingAction.APPROVE;
        raced(()->publishing.decide(tool,first,1,admin),()->publishing.decide(tool,second,1,admin),false,InvalidStateTransitionException.class);
        assertEquals(first==PublishingAction.APPROVE?"PUBLISHED":"DRAFT",state());assertEquals(1,rev());
    }
    @Test void rolledBackSubmitReleasesLockAndDoesNotConsumeRevision()throws Exception{
        raced(()->publishing.transition(tool,PublishingAction.SUBMIT,owner),()->mutate("create"),true,null);
        assertEquals("DRAFT",state());assertEquals(0,rev());
        assertEquals(2,jdbc.queryForObject("select count(*) from tool_versions where tool_id=?",Integer.class,tool));
    }
    @Test void rolledBackVersionWriteDoesNotLeakIntoSubmission()throws Exception{
        raced(()->mutate("update"),()->publishing.transition(tool,PublishingAction.SUBMIT,owner),true,null);
        assertEquals("PENDING",state());assertEquals(1,rev());
        assertEquals("original",jdbc.queryForObject("select version from tool_versions where id=?",String.class,version));
    }
    @Test void rolledBackDecisionDoesNotPublishAndWaiterCanReject()throws Exception{
        publishing.transition(tool,PublishingAction.SUBMIT,owner);
        raced(()->publishing.decide(tool,PublishingAction.APPROVE,1,admin),()->publishing.decide(tool,PublishingAction.REJECT,1,admin),true,null);
        assertEquals("DRAFT",state());assertEquals(1,rev());
    }
    private static class Rollback extends RuntimeException{}
    void raced(Runnable first,Runnable second,boolean rollback,Class<? extends Throwable> expected)throws Exception{
        CountDownLatch ready=new CountDownLatch(1),release=new CountDownLatch(1),started=new CountDownLatch(1);
        AtomicInteger firstPid=new AtomicInteger(),secondPid=new AtomicInteger();
        Future<Throwable> a=workers.submit(()->outcome(()->{
            firstPid.set(jdbc.queryForObject("select pg_backend_pid()",Integer.class));first.run();em.flush();ready.countDown();await(release);
            if(rollback)throw new Rollback();
        }));
        try{
            await(ready);
            Future<Throwable> b=workers.submit(()->outcome(()->{secondPid.set(jdbc.queryForObject("select pg_backend_pid()",Integer.class));started.countDown();second.run();}));
            await(started);boolean blocked=false;long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<until&&!b.isDone()){
                if(Boolean.TRUE.equals(jdbc.queryForObject("select ? = any(pg_blocking_pids(?))",Boolean.class,firstPid.get(),secondPid.get()))){blocked=true;break;}
                Thread.sleep(20);
            }
            release.countDown();Throwable x=a.get(15,TimeUnit.SECONDS),y=b.get(15,TimeUnit.SECONDS);
            if(rollback)assertInstanceOf(Rollback.class,x);else assertNull(x);
            assertTrue(blocked,"Second transaction must actually block on the first row lock");
            if(expected==null)assertNull(y);else assertInstanceOf(expected,y);
        }finally{release.countDown();}
    }
    Throwable outcome(Runnable r){try{tx(r);return null;}catch(Throwable t){return t;}}
    static void await(CountDownLatch latch){try{assertTrue(latch.await(15,TimeUnit.SECONDS),"Latch timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
}
