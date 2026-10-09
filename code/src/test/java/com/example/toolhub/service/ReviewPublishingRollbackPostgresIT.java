package com.example.toolhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.toolhub.domain.enums.PublishingAction;
import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.dto.request.UpdateReviewRequest;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.support.PostgresTestDatabaseGuard;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Real service/transaction regressions for review and publishing lock rollback ordering. */
@SpringBootTest(properties = {
        "spring.datasource.url=${PRIMESKILL_TEST_DB_URL}",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = PostgresTestDatabaseGuard.class)
class ReviewPublishingRollbackPostgresIT {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private ReviewService reviews;
    @Autowired private PublishingService publishing;
    @Autowired private PlatformTransactionManager transactionManager;

    private ExecutorService workers;
    private long ownerId;
    private long reviewerId;
    private long toolId;
    private Long reviewId;

    @BeforeEach
    void createFixture() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
        }

        String suffix = UUID.randomUUID().toString();
        ownerId = jdbc.queryForObject(
                "INSERT INTO users(email,password_hash) VALUES(?, 'test-only') RETURNING id",
                Long.class, "rollback-owner-" + suffix + "@example.test");
        reviewerId = jdbc.queryForObject(
                "INSERT INTO users(email,password_hash) VALUES(?, 'test-only') RETURNING id",
                Long.class, "rollback-reviewer-" + suffix + "@example.test");
        long categoryId = jdbc.queryForObject(
                "INSERT INTO categories(name,slug) VALUES(?, ?) RETURNING id",
                Long.class, "Rollback " + suffix, "rollback-" + suffix);
        toolId = jdbc.queryForObject(
                "INSERT INTO tools(owner_id,category_id,name,slug,short_description,description,status) "
                        + "VALUES(?,?,?,?,'Short','Details','PUBLISHED') RETURNING id",
                Long.class, ownerId, categoryId, "Rollback tool " + suffix, "rollback-tool-" + suffix);
        reviewId = null;
        workers = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopWorkers() throws Exception {
        if (workers != null) {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS), "Workers must terminate");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update"})
    void reviewRollbackReleasesToolLockAndPublishingServiceCanDeprecate(String operation) throws Exception {
        seedReviewIfUpdating(operation);
        CountDownLatch reviewReady = new CountDownLatch(1);
        CountDownLatch releaseReview = new CountDownLatch(1);
        CountDownLatch publisherStarted = new CountDownLatch(1);
        AtomicInteger reviewerPid = new AtomicInteger();
        AtomicInteger publisherPid = new AtomicInteger();

        Future<Outcome> reviewTransaction = workers.submit(() -> inTransaction(reviewerPid, true, () -> {
            writeReview(operation, "rolled back");
            reviewReady.countDown();
            await(releaseReview);
        }));

        try {
            await(reviewReady);
            Future<Outcome> publishingTransaction = workers.submit(() -> inTransaction(publisherPid, false, () -> {
                publisherStarted.countDown();
                deprecate();
            }));
            await(publisherStarted);

            assertTrue(waitForDatabaseBlockOrCompletion(
                            publishingTransaction, publisherPid.get(), reviewerPid.get()),
                    "PublishingService must be waiting on the review transaction's Tool row lock");
            releaseReview.countDown();

            assertNull(reviewTransaction.get(15, TimeUnit.SECONDS).failure());
            assertNull(publishingTransaction.get(15, TimeUnit.SECONDS).failure());
            assertEquals("DEPRECATED", statusOfTool());
            assertReviewAfterRollback(operation, "original", (short) 3);
        } finally {
            releaseReview.countDown();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update"})
    void publishingRollbackReleasesToolLockAndReviewServiceCanWrite(String operation) throws Exception {
        seedReviewIfUpdating(operation);
        CountDownLatch publishingReady = new CountDownLatch(1);
        CountDownLatch releasePublishing = new CountDownLatch(1);
        CountDownLatch reviewerStarted = new CountDownLatch(1);
        AtomicInteger publisherPid = new AtomicInteger();
        AtomicInteger reviewerPid = new AtomicInteger();

        Future<Outcome> publishingTransaction = workers.submit(() -> inTransaction(publisherPid, true, () -> {
            deprecate();
            publishingReady.countDown();
            await(releasePublishing);
        }));

        try {
            await(publishingReady);
            Future<Outcome> reviewTransaction = workers.submit(() -> inTransaction(reviewerPid, false, () -> {
                reviewerStarted.countDown();
                writeReview(operation, "saved after rollback");
            }));
            await(reviewerStarted);

            assertTrue(waitForDatabaseBlockOrCompletion(
                            reviewTransaction, reviewerPid.get(), publisherPid.get()),
                    "ReviewService must be waiting on the publishing transaction's Tool row lock");
            releasePublishing.countDown();

            assertNull(publishingTransaction.get(15, TimeUnit.SECONDS).failure());
            assertNull(reviewTransaction.get(15, TimeUnit.SECONDS).failure());
            assertEquals("PUBLISHED", statusOfTool());
            assertReviewAfterRollback(operation, "saved after rollback", (short) 5);
        } finally {
            releasePublishing.countDown();
        }
    }

    private void seedReviewIfUpdating(String operation) {
        if ("update".equals(operation)) {
            reviewId = jdbc.queryForObject(
                    "INSERT INTO reviews(user_id,tool_id,rating,comment) VALUES(?,?,3,'original') RETURNING id",
                    Long.class, reviewerId, toolId);
        }
    }

    private void writeReview(String operation, String comment) {
        if ("create".equals(operation)) {
            reviews.create(toolId, new CreateReviewRequest((short) 5, comment), reviewerId, false);
        } else {
            reviews.update(toolId, reviewId, new UpdateReviewRequest((short) 5, comment), reviewerId, false);
        }
    }

    private void deprecate() {
        publishing.transition(toolId, PublishingAction.DEPRECATE, new CurrentActor(ownerId, false));
    }

    private Outcome inTransaction(AtomicInteger backendPid, boolean rollback, Runnable action) {
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            transaction.setTimeout(20);
            transaction.executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL lock_timeout='10s'");
                jdbc.execute("SET LOCAL statement_timeout='15s'");
                backendPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                action.run();
                if (rollback) {
                    status.setRollbackOnly();
                }
            });
            return new Outcome(null);
        } catch (RuntimeException failure) {
            return new Outcome(failure);
        }
    }

    private boolean waitForDatabaseBlockOrCompletion(Future<?> waiterFuture, int waiterPid, int blockerPid)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        do {
            if (waiterPid > 0 && blockerPid > 0) {
                Boolean blocked = jdbc.queryForObject(
                        "SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, blockerPid, waiterPid);
                if (Boolean.TRUE.equals(blocked)) {
                    return true;
                }
            }
            if (waiterFuture.isDone()) {
                return false;
            }
            // Polling only yields CPU; PostgreSQL's blocking PID list is the evidence under test.
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        } while (System.nanoTime() < deadline);
        throw new AssertionError("No PostgreSQL lock wait or completed operation within 8 seconds");
    }

    private void assertReviewAfterRollback(String operation, String expectedComment, short expectedRating) {
        if ("create".equals(operation) && "original".equals(expectedComment)) {
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM reviews WHERE tool_id=?", Integer.class, toolId));
            return;
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM reviews WHERE tool_id=?", Integer.class, toolId));
        Integer rating = jdbc.queryForObject("SELECT rating FROM reviews WHERE tool_id=?", Integer.class, toolId);
        String comment = jdbc.queryForObject("SELECT comment FROM reviews WHERE tool_id=?", String.class, toolId);
        assertEquals(expectedRating, rating.shortValue());
        assertEquals(expectedComment, comment);
    }

    private String statusOfTool() {
        return jdbc.queryForObject("SELECT status FROM tools WHERE id=?", String.class, toolId);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(12, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out awaiting transaction race barrier");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private record Outcome(RuntimeException failure) { }
}
