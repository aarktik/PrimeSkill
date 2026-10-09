package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.*;

import com.example.toolhub.domain.entity.*;
import com.example.toolhub.domain.enums.*;
import com.example.toolhub.dto.request.UpdateToolRequest;
import com.example.toolhub.exception.InvalidStateTransitionException;
import com.example.toolhub.repository.*;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.service.*;
import com.example.toolhub.support.PostgresTestDatabaseGuard;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"spring.datasource.url=${PRIMESKILL_TEST_DB_URL}", "spring.jpa.open-in-view=false"})
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = PostgresTestDatabaseGuard.class)
class ToolMetadataConcurrencyPostgresIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired PublishingService publishing;
    @Autowired ToolService catalog;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ToolRepository tools;
    long tool, category, replacementCategory, ownerId;
    CurrentActor owner, admin;
    ExecutorService workers;
    UpdateToolRequest request;

    @BeforeEach void seed() {
        String suffix = UUID.randomUUID().toString();
        ownerId = users.saveAndFlush(new User(suffix + "@test.invalid", "test-only")).getId();
        Category original = categories.saveAndFlush(new Category(suffix, "original-" + suffix, null));
        category = original.getId();
        replacementCategory = categories.saveAndFlush(new Category("Replacement " + suffix, "replacement-" + suffix, null)).getId();
        tool = tools.saveAndFlush(new Tool(ownerId, original, "Original", "tool-" + suffix,
                "Original short", "Original details", "https://example.invalid/original")).getId();
        jdbc.update("update tools set view_count=17 where id=?", tool);
        owner = new CurrentActor(ownerId, false);
        admin = new CurrentActor(ownerId, true);
        request = new UpdateToolRequest("Changed", "changed-" + suffix, "Changed short",
                "Changed details", replacementCategory, "https://example.invalid/changed");
        workers = Executors.newFixedThreadPool(2);
    }
    @AfterEach void cleanup() throws Exception {
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        jdbc.update("delete from tools where id=?", tool);
        jdbc.update("delete from categories where id in (?,?)", category, replacementCategory);
        jdbc.update("delete from users where id=?", ownerId);
    }
    void update() { catalog.update(tool, request, ownerId, false); }
    void submit() { publishing.transition(tool, PublishingAction.SUBMIT, owner); }
    void approve() { publishing.decide(tool, PublishingAction.APPROVE, 1, admin); }
    Map<String, Object> metadata() {
        return jdbc.queryForMap("select id,owner_id,category_id,name,slug,short_description,description,repository_url,view_count from tools where id=?", tool);
    }
    void assertState(String state, long revision) {
        assertEquals(state, jdbc.queryForObject("select status from tools where id=?", String.class, tool));
        assertEquals(revision, jdbc.queryForObject("select review_revision from tools where id=?", Long.class, tool));
        assertEquals(17L, metadata().get("view_count"));
        assertEquals(ownerId, metadata().get("owner_id"));
    }
    void assertChanged() {
        Map<String, Object> actual = metadata();
        assertAll(() -> assertEquals(request.name(), actual.get("name")), () -> assertEquals(request.slug(), actual.get("slug")),
                () -> assertEquals(request.shortDescription(), actual.get("short_description")),
                () -> assertEquals(request.description(), actual.get("description")),
                () -> assertEquals(request.repositoryUrl(), actual.get("repository_url")),
                () -> assertEquals(replacementCategory, actual.get("category_id")));
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void metadataAndSubmitSerializeBothOrders(boolean metadataFirst) throws Exception {
        Map<String, Object> before = metadata();
        raced(metadataFirst ? this::update : this::submit, metadataFirst ? this::submit : this::update,
                null, metadataFirst ? null : InvalidStateTransitionException.class, false);
        assertState("PENDING", 1);
        if (metadataFirst) assertChanged(); else assertEquals(before, metadata());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void metadataCannotChangePendingCandidateInEitherApproveOrder(boolean metadataFirst) throws Exception {
        submit();
        Map<String, Object> before = metadata();
        raced(metadataFirst ? this::update : this::approve, metadataFirst ? this::approve : this::update,
                metadataFirst ? InvalidStateTransitionException.class : null,
                metadataFirst ? null : InvalidStateTransitionException.class, false);
        assertState("PUBLISHED", 1);
        assertEquals(before, metadata());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void staleManagedDraftCannotBypassSubmittedOrPublishedState(boolean approveAfterSubmit) throws Exception {
        Map<String, Object> before = metadata();
        TransactionTemplate transaction = new TransactionTemplate(manager);
        transaction.setTimeout(20);
        transaction.executeWithoutResult(status -> {
            Tool cached = em.find(Tool.class, tool);
            assertEquals(ToolStatus.DRAFT, cached.getStatus());
            try {
                workers.submit(() -> { submit(); if (approveAfterSubmit) approve(); }).get(15, TimeUnit.SECONDS);
            } catch (Exception exception) { throw new AssertionError(exception); }
            assertEquals(ToolStatus.DRAFT, cached.getStatus(), "Prove JPA entity is stale before invoking update");
            assertThrows(InvalidStateTransitionException.class, this::update);
            assertEquals(approveAfterSubmit ? ToolStatus.PUBLISHED : ToolStatus.PENDING, cached.getStatus(), "Lock + refresh must read committed state");
            status.setRollbackOnly();
        });
        assertState(approveAfterSubmit ? "PUBLISHED" : "PENDING", 1);
        assertEquals(before, metadata());
    }

    @Test void rollbackOfMetadataDoesNotLeakIntoWaitingSubmission() throws Exception {
        Map<String, Object> before = metadata();
        raced(this::update, this::submit, null, null, true);
        assertState("PENDING", 1);
        assertEquals(before, metadata());
    }

    @Test void rollbackOfSubmissionDoesNotConsumeRevisionOrDenyWaitingEdit() throws Exception {
        raced(this::submit, this::update, null, null, true);
        assertState("DRAFT", 0);
        assertChanged();
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void otherUserCannotEditInEitherApproveOrder(boolean otherFirst) throws Exception {
        submit();
        Map<String, Object> before = metadata();
        Runnable denied = () -> catalog.update(tool, request, ownerId + 1000000, false);
        raced(otherFirst ? denied : this::approve, otherFirst ? this::approve : denied,
                otherFirst ? AccessDeniedException.class : null, otherFirst ? null : AccessDeniedException.class, false);
        assertState("PUBLISHED", 1);
        assertEquals(before, metadata());
    }

    @Test void lockTimeoutRollsBackWithoutPartialMetadata() throws Exception {
        Map<String, Object> before = metadata();
        raced(this::update, () -> { jdbc.execute("set local lock_timeout='500ms'"); update(); },
                null, PessimisticLockingFailureException.class, true, true);
        assertState("DRAFT", 0);
        assertEquals(before, metadata());
    }

    void raced(Runnable first, Runnable second, Class<? extends Throwable> firstDenied,
               Class<? extends Throwable> secondDenied, boolean rollbackFirst) throws Exception {
        raced(first, second, firstDenied, secondDenied, rollbackFirst, false);
    }
    void raced(Runnable first, Runnable second, Class<? extends Throwable> firstDenied,
               Class<? extends Throwable> secondDenied, boolean rollbackFirst, boolean waitForSecondFailure) throws Exception {
        CountDownLatch ready = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger(), secondPid = new AtomicInteger();
        Future<Throwable> a = workers.submit(() -> outcome(() -> {
            TransactionTemplate transaction = new TransactionTemplate(manager);
            transaction.setTimeout(20);
            transaction.executeWithoutResult(status -> {
                firstPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                if (firstDenied == null) { first.run(); em.flush(); }
                else { assertThrows(firstDenied, first::run); status.setRollbackOnly(); }
                ready.countDown(); await(release);
                if (rollbackFirst) status.setRollbackOnly();
            });
        }));
        try {
            await(ready);
            Future<Throwable> b = workers.submit(() -> outcome(() -> {
                TransactionTemplate transaction = new TransactionTemplate(manager);
                transaction.setTimeout(20);
                transaction.executeWithoutResult(status -> {
                    secondPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class)); started.countDown(); second.run();
                });
            }));
            await(started);
            boolean blocked = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline && !b.isDone()) {
                if (Boolean.TRUE.equals(jdbc.queryForObject("select ? = any(pg_blocking_pids(?))", Boolean.class, firstPid.get(), secondPid.get()))) {
                    blocked = true; break;
                }
                Thread.sleep(20);
            }
            assertTrue(blocked, "Prove the second connection waits on the shared Tool row lock");
            Throwable secondResult = null;
            if (waitForSecondFailure) secondResult = b.get(15, TimeUnit.SECONDS);
            release.countDown();
            assertNull(a.get(15, TimeUnit.SECONDS));
            if (!waitForSecondFailure) secondResult = b.get(15, TimeUnit.SECONDS);
            if (secondDenied == null) assertNull(secondResult); else assertInstanceOf(secondDenied, secondResult);
        } finally { release.countDown(); }
    }
    static Throwable outcome(Runnable action) { try { action.run(); return null; } catch (Throwable failure) { return failure; } }
    static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(15, TimeUnit.SECONDS), "Latch timeout"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
}
