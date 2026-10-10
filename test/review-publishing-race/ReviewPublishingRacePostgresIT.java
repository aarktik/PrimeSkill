package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.*;

import com.example.toolhub.domain.enums.PublishingAction;
import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.dto.request.UpdateReviewRequest;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.ResourceNotFoundException;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.service.PublishingService;
import com.example.toolhub.service.ReviewService;
import com.example.toolhub.support.PostgresTestDatabaseGuard;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Requires the explicitly composed D review + E publishing checkout, never a shared DB. */
@SpringBootTest(properties="spring.datasource.url=${PRIMESKILL_TEST_DB_URL}")
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers=PostgresTestDatabaseGuard.class)
class ReviewPublishingRacePostgresIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ReviewService reviews;
    @Autowired PublishingService publishing;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager entityManager;
    private ExecutorService workers;
    private long owner, reviewer, tool, review;

    @BeforeEach
    void fixture() throws Exception {
        try(var connection=dataSource.getConnection()){
            assertEquals("PostgreSQL",connection.getMetaData().getDatabaseProductName());
        }
        for(String table:new String[]{"reviews","tools","categories","user_profiles","users"})jdbc.update("DELETE FROM "+table);
        owner=jdbc.queryForObject("INSERT INTO users(email,password_hash) VALUES('race-owner@example.test','test-only') RETURNING id",Long.class);
        reviewer=jdbc.queryForObject("INSERT INTO users(email,password_hash) VALUES('race-reviewer@example.test','test-only') RETURNING id",Long.class);
        long category=jdbc.queryForObject("INSERT INTO categories(name,slug) VALUES('Race','race') RETURNING id",Long.class);
        tool=jdbc.queryForObject("INSERT INTO tools(owner_id,category_id,name,slug,short_description,description,status) "
                +"VALUES(?,?,'Race tool','race-tool','Short','Details','PUBLISHED') RETURNING id",Long.class,owner,category);
        workers=Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopWorkers() throws Exception {
        if(workers!=null){workers.shutdownNow();assertTrue(workers.awaitTermination(15,TimeUnit.SECONDS),"Workers must terminate");}
    }

    @ParameterizedTest
    @ValueSource(strings={"create","update"})
    void deprecateFirstRejectsReviewAfterWaitingForToolLock(String operation) throws Exception {
        seedUpdate(operation);
        CountDownLatch firstReady=new CountDownLatch(1), releaseFirst=new CountDownLatch(1), secondStarted=new CountDownLatch(1);
        AtomicInteger publisherPid=new AtomicInteger(), reviewerPid=new AtomicInteger();
        Future<Outcome> publisher=workers.submit(()->transaction(publisherPid,()->{
            deprecate(); firstReady.countDown(); await(releaseFirst);
        }));
        try {
            await(firstReady);
            Future<Outcome> attempt=workers.submit(()->transaction(reviewerPid,()->{
                secondStarted.countDown(); writeReview(operation);
            }));
            await(secondStarted);
            boolean blocked=waitForDatabaseBlockOrCompletion(attempt,reviewerPid.get(),publisherPid.get());
            releaseFirst.countDown();
            assertNull(publisher.get(15,TimeUnit.SECONDS).failure());
            Outcome result=attempt.get(15,TimeUnit.SECONDS);
            assertTrue(result.failure() instanceof ResourceNotFoundException || result.failure() instanceof CatalogConflictException,
                    "Review must be rejected after deprecate commits; actual="+result.failure());
            assertTrue(blocked,"Review must serialize with the publishing tool-row lock");
            assertEquals("DEPRECATED",jdbc.queryForObject("SELECT status FROM tools WHERE id=?",String.class,tool));
            if(operation.equals("create"))assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM reviews",Integer.class));
            else assertEquals("original",jdbc.queryForObject("SELECT comment FROM reviews WHERE id=?",String.class,review));
        } finally { releaseFirst.countDown(); }
    }

    @ParameterizedTest
    @ValueSource(strings={"create","update"})
    void reviewFirstCommitsBeforeDeprecateAndReviewIsPreserved(String operation) throws Exception {
        seedUpdate(operation);
        CountDownLatch firstReady=new CountDownLatch(1), releaseFirst=new CountDownLatch(1), secondStarted=new CountDownLatch(1);
        AtomicInteger reviewerPid=new AtomicInteger(), publisherPid=new AtomicInteger();
        Future<Outcome> writer=workers.submit(()->transaction(reviewerPid,()->{
            writeReview(operation); firstReady.countDown(); await(releaseFirst);
        }));
        try {
            await(firstReady);
            Future<Outcome> transition=workers.submit(()->transaction(publisherPid,()->{
                secondStarted.countDown(); deprecate();
            }));
            await(secondStarted);
            boolean blocked=waitForDatabaseBlockOrCompletion(transition,publisherPid.get(),reviewerPid.get());
            releaseFirst.countDown();
            assertNull(writer.get(15,TimeUnit.SECONDS).failure());
            assertNull(transition.get(15,TimeUnit.SECONDS).failure());
            assertTrue(blocked,"Deprecate must wait for the in-flight review transaction");
            assertEquals("DEPRECATED",jdbc.queryForObject("SELECT status FROM tools WHERE id=?",String.class,tool));
            assertEquals("accepted",jdbc.queryForObject("SELECT comment FROM reviews WHERE tool_id=?",String.class,tool));
            long saved=jdbc.queryForObject("SELECT id FROM reviews WHERE tool_id=?",Long.class,tool);
            reviews.delete(tool,saved,reviewer,false);
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM reviews",Integer.class));
        } finally { releaseFirst.countDown(); }
    }

    @ParameterizedTest
    @CsvSource({"review,create", "review,update", "deprecate,create", "deprecate,update"})
    void rollbackReleasesLockAndPreservesOnlyCommittedChanges(String rolledBack, String operation) throws Exception {
        seedUpdate(operation);
        boolean reviewRollsBack=rolledBack.equals("review");
        CountDownLatch firstReady=new CountDownLatch(1), releaseFirst=new CountDownLatch(1), secondStarted=new CountDownLatch(1);
        AtomicInteger firstPid=new AtomicInteger(), secondPid=new AtomicInteger();
        Future<Outcome> first=workers.submit(()->transaction(firstPid,()->{
            if(reviewRollsBack)writeReview(operation);else deprecate();
            entityManager.flush(); // Ensure rollback undoes actual SQL, including update's dirty state.
            firstReady.countDown();
            await(releaseFirst);
            throw new DeliberateRollback();
        }));
        try {
            await(firstReady);
            Future<Outcome> second=workers.submit(()->transaction(secondPid,()->{
                secondStarted.countDown();
                if(reviewRollsBack)deprecate();else writeReview(operation);
            }));
            await(secondStarted);
            boolean blocked=waitForDatabaseBlockOrCompletion(second,secondPid.get(),firstPid.get());
            releaseFirst.countDown();
            assertInstanceOf(DeliberateRollback.class,first.get(15,TimeUnit.SECONDS).failure());
            assertNull(second.get(15,TimeUnit.SECONDS).failure(),"Waiter must succeed after rollback releases the lock");
            assertTrue(blocked,"Second transaction must wait for the first before its rollback");
            assertEquals(reviewRollsBack?"DEPRECATED":"PUBLISHED",
                    jdbc.queryForObject("SELECT status FROM tools WHERE id=?",String.class,tool));
            if(reviewRollsBack && operation.equals("create")) {
                assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM reviews WHERE tool_id=?",Integer.class,tool));
            } else {
                assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM reviews WHERE tool_id=?",Integer.class,tool));
                assertEquals(reviewRollsBack?"original":"accepted",
                        jdbc.queryForObject("SELECT comment FROM reviews WHERE tool_id=?",String.class,tool));
                assertEquals(reviewRollsBack?4:5,
                        jdbc.queryForObject("SELECT rating FROM reviews WHERE tool_id=?",Integer.class,tool));
            }
        } finally { releaseFirst.countDown(); }
    }

    private static final class DeliberateRollback extends RuntimeException {}

    private void seedUpdate(String operation){
        if(operation.equals("update"))review=jdbc.queryForObject(
                "INSERT INTO reviews(user_id,tool_id,rating,comment) VALUES(?,?,4,'original') RETURNING id",Long.class,reviewer,tool);
    }

    @ParameterizedTest
    @ValueSource(strings={"create","update"})
    void previouslyManagedToolMustNotReuseStatusFromBeforeDeprecate(String operation) throws Exception {
        seedUpdate(operation);
        RuntimeException failure=assertThrows(RuntimeException.class,()->{
            TransactionTemplate tx=new TransactionTemplate(manager);
            tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            tx.setTimeout(20);
            tx.executeWithoutResult(status->{
                Tool cached=entityManager.find(Tool.class,tool);
                assertEquals("PUBLISHED",cached.getStatus().name());
                try {
                    Outcome transition=workers.submit(()->transaction(new AtomicInteger(),this::deprecate)).get(15,TimeUnit.SECONDS);
                    assertNull(transition.failure());
                } catch(Exception unexpected){throw new IllegalStateException(unexpected);}
                assertEquals("PUBLISHED",cached.getStatus().name(),"Fixture must actually contain a stale managed entity");
                writeReview(operation);
            });
        });
        assertTrue(failure instanceof ResourceNotFoundException || failure instanceof CatalogConflictException,
                "Expected visibility/state rejection, actual="+failure);
        assertEquals("DEPRECATED",jdbc.queryForObject("SELECT status FROM tools WHERE id=?",String.class,tool));
        if(operation.equals("create"))assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM reviews",Integer.class));
        else assertEquals("original",jdbc.queryForObject("SELECT comment FROM reviews WHERE id=?",String.class,review));
    }
    private void writeReview(String operation){
        if(operation.equals("create"))reviews.create(tool,new CreateReviewRequest((short)5,"accepted"),reviewer,false);
        else reviews.update(tool,review,new UpdateReviewRequest((short)5,"accepted"),reviewer,false);
    }
    private void deprecate(){publishing.transition(tool,PublishingAction.DEPRECATE,new CurrentActor(owner,false));}

    private Outcome transaction(AtomicInteger pid,Runnable action){
        try {
            TransactionTemplate tx=new TransactionTemplate(manager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            tx.setTimeout(20);
            tx.executeWithoutResult(status->{
                jdbc.execute("SET LOCAL lock_timeout='10s'");
                jdbc.execute("SET LOCAL statement_timeout='15s'");
                pid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
                action.run();
            });
            return new Outcome(null);
        } catch(RuntimeException failure){return new Outcome(failure);}
    }
    private boolean waitForDatabaseBlockOrCompletion(Future<?> future,int waiter,int blocker){
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        do {
            Boolean blocked=jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))",Boolean.class,blocker,waiter);
            if(Boolean.TRUE.equals(blocked))return true;
            if(future.isDone())return false;
            // Polling only yields CPU; the assertion relies on PostgreSQL's actual blocking PID evidence.
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        } while(System.nanoTime()<deadline);
        throw new AssertionError("No PostgreSQL lock wait or completed operation within timeout");
    }
    private static void await(CountDownLatch latch){
        try {if(!latch.await(12,TimeUnit.SECONDS))throw new IllegalStateException("Timed out awaiting race barrier");}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
    }
    private record Outcome(RuntimeException failure){}
}
