package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.*;

import com.example.toolhub.domain.entity.*;
import com.example.toolhub.domain.enums.*;
import com.example.toolhub.exception.*;
import com.example.toolhub.repository.*;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.service.*;
import com.example.toolhub.support.PostgresTestDatabaseGuard;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"spring.datasource.url=${PRIMESKILL_TEST_DB_URL}", "spring.jpa.open-in-view=false"})
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = PostgresTestDatabaseGuard.class)
class TagMutationConcurrencyPostgresIT {
    @Autowired TagService tags;
    @Autowired PublishingService publishing;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ToolRepository tools;
    @Autowired TagRepository tagRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    long owner, category, tool, tag;
    ExecutorService workers;

    @BeforeEach void seed() {
        String suffix = UUID.randomUUID().toString();
        owner = users.saveAndFlush(new User("tag-race-" + suffix + "@test.invalid", "unused")).getId();
        Category entity = categories.saveAndFlush(new Category(suffix, suffix, null));
        category = entity.getId();
        tool = tools.saveAndFlush(new Tool(owner, entity, "Tag race", suffix, "Short", "Details", null)).getId();
        tag = tagRepository.saveAndFlush(new Tag(suffix, suffix)).getId();
        jdbc.update("UPDATE tools SET view_count=17 WHERE id=?", tool);
        workers = Executors.newFixedThreadPool(2);
    }

    @AfterEach void cleanup() throws Exception {
        workers.shutdownNow(); assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        jdbc.update("DELETE FROM tools WHERE id=?", tool);
        jdbc.update("DELETE FROM tags WHERE id=?", tag);
        jdbc.update("DELETE FROM categories WHERE id=?", category);
        jdbc.update("DELETE FROM users WHERE id=?", owner);
    }

    void assign() { tags.assignTag(tool, tag, owner, false); }
    void unassign() { tags.unassignTag(tool, tag, owner, false); }
    void deleteTag() { tags.delete(tag, true); }
    void submit() { publishing.transition(tool, PublishingAction.SUBMIT, new CurrentActor(owner, false)); }
    void approve() { publishing.decide(tool, PublishingAction.APPROVE, 1, new CurrentActor(owner, true)); }
    void reject() { publishing.decide(tool, PublishingAction.REJECT, 1, new CurrentActor(owner, true)); }
    void link() { jdbc.update("INSERT INTO tool_tags(tool_id,tag_id) VALUES(?,?)", tool, tag); }
    long links() { return jdbc.queryForObject("SELECT COUNT(*) FROM tool_tags WHERE tool_id=? AND tag_id=?", Long.class, tool, tag); }
    Map<String, Object> snapshot() { return jdbc.queryForMap("SELECT * FROM tools WHERE id=?", tool); }
    void state(String state, long revision) {
        var row = snapshot();
        assertEquals(state, row.get("status")); assertEquals(revision, row.get("review_revision"));
        assertEquals(17L, row.get("view_count")); assertEquals(owner, row.get("owner_id"));
        assertEquals("Tag race", row.get("name"));
    }

    static java.util.stream.Stream<Arguments> orders() {
        return java.util.stream.Stream.of(true, false).flatMap(assign ->
                java.util.stream.Stream.of(true, false).map(first -> Arguments.of(assign, first)));
    }

    @ParameterizedTest(name = "assign={0}/mutationFirst={1}") @MethodSource("orders")
    void tagMutationAndSubmissionSerializeBothCommitOrders(boolean assign, boolean mutationFirst) throws Exception {
        if (!assign) link();
        Runnable mutation = assign ? this::assign : this::unassign;
        raced(mutationFirst ? mutation : this::submit, mutationFirst ? this::submit : mutation,
                null, mutationFirst ? null : InvalidStateTransitionException.class, false);
        state("PENDING", 1);
        assertEquals(mutationFirst ? (assign ? 1 : 0) : (assign ? 0 : 1), links());
    }

    @ParameterizedTest(name = "assign={0}/mutationFirst={1}") @MethodSource("orders")
    void tagMutationAndApprovalAreDeniedInBothOrders(boolean assign, boolean mutationFirst) throws Exception {
        if (!assign) link(); submit();
        Runnable mutation = assign ? this::assign : this::unassign;
        raced(mutationFirst ? mutation : this::approve, mutationFirst ? this::approve : mutation,
                mutationFirst ? InvalidStateTransitionException.class : null,
                mutationFirst ? null : InvalidStateTransitionException.class, false);
        state("PUBLISHED", 1); assertEquals(assign ? 0 : 1, links());
    }

    @ParameterizedTest(name = "assign={0}/mutationFirst={1}") @MethodSource("orders")
    void tagMutationAndRejectionSerializeBothOrders(boolean assign, boolean mutationFirst) throws Exception {
        if (!assign) link(); submit();
        Map<String, Object> before = snapshot();
        Runnable mutation = assign ? this::assign : this::unassign;
        // A writer locking PENDING first is denied. A writer waiting for a
        // committed REJECT must refresh and may edit the resulting DRAFT.
        raced(mutationFirst ? mutation : this::reject, mutationFirst ? this::reject : mutation,
                mutationFirst ? InvalidStateTransitionException.class : null, null, false);
        state("DRAFT", 1);
        assertEquals(mutationFirst ? (assign ? 0 : 1) : (assign ? 1 : 0), links());
        assertMetadataUnchanged(before);
        assertTrue(tagRepository.existsById(tag));
        submit(); state("PENDING", 2);
        Map<String, Object> resubmitted = snapshot();
        assertThrows(StaleReviewRevisionException.class, this::reject);
        assertThrows(StaleReviewRevisionException.class, this::approve);
        assertEquals(resubmitted, snapshot());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void rolledBackRejectionKeepsWaitingTagMutationDenied(boolean assign) throws Exception {
        if (!assign) link(); submit();
        Map<String, Object> before = snapshot();
        raced(this::reject, assign ? this::assign : this::unassign,
                null, InvalidStateTransitionException.class, true);
        state("PENDING", 1); assertEquals(assign ? 0 : 1, links());
        assertEquals(before, snapshot(), "Rolled back REJECT must not persist any Tool changes");
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void rolledBackRejectionAndTagMutationDoNotLeakIntoWaitingRejection(boolean assign) throws Exception {
        if (!assign) link(); submit();
        Map<String, Object> before = snapshot();
        raced(() -> { reject(); if (assign) assign(); else unassign(); }, this::reject,
                null, null, true);
        state("DRAFT", 1); assertEquals(assign ? 0 : 1, links());
        assertMetadataUnchanged(before);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void rolledBackTagMutationAfterRejectionDoesNotLeakIntoResubmission(boolean assign) throws Exception {
        if (!assign) link(); submit(); reject();
        Map<String, Object> before = snapshot();
        raced(assign ? this::assign : this::unassign, this::submit, null, null, true);
        state("PENDING", 2); assertEquals(assign ? 0 : 1, links());
        assertMetadataUnchanged(before);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void staleManagedPendingAllowsTagMutationAfterCommittedRejection(boolean assign) throws Exception {
        if (!assign) link(); submit();
        Map<String, Object> before = snapshot();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            Tool cached = em.find(Tool.class, tool);
            assertEquals(ToolStatus.PENDING, cached.getStatus());
            try { workers.submit(this::reject).get(15, TimeUnit.SECONDS); }
            catch (Exception exception) { throw new AssertionError(exception); }
            assertEquals(ToolStatus.PENDING, cached.getStatus(), "Prove cached PENDING is stale");
            if (assign) assign(); else unassign();
            assertEquals(ToolStatus.DRAFT, cached.getStatus(), "Shared lock must refresh committed REJECT");
        });
        state("DRAFT", 1); assertEquals(assign ? 1 : 0, links());
        assertMetadataUnchanged(before);
    }

    void assertMetadataUnchanged(Map<String, Object> before) {
        var expected = new HashMap<>(before);
        var actual = new HashMap<>(snapshot());
        for (String key : List.of("status", "review_revision", "updated_at")) {
            expected.remove(key); actual.remove(key);
        }
        assertEquals(expected, actual, "Only publishing state/revision/timestamp may change");
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void rolledBackTagMutationDoesNotLeakIntoWaitingSubmission(boolean assign) throws Exception {
        if (!assign) link();
        raced(assign ? this::assign : this::unassign, this::submit, null, null, true);
        state("PENDING", 1); assertEquals(assign ? 0 : 1, links());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void rolledBackSubmissionAllowsWaitingDraftMutation(boolean assign) throws Exception {
        if (!assign) link();
        raced(this::submit, assign ? this::assign : this::unassign, null, null, true);
        state("DRAFT", 0); assertEquals(assign ? 1 : 0, links());
    }

    @ParameterizedTest @MethodSource("orders")
    void staleManagedDraftCannotBypassCommittedSubmissionOrApproval(boolean assign, boolean approved) {
        if (!assign) link();
        Map<String, Object> before = snapshot();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            Tool cached = em.find(Tool.class, tool);
            assertEquals(ToolStatus.DRAFT, cached.getStatus());
            try { workers.submit(() -> { submit(); if (approved) approve(); }).get(15, TimeUnit.SECONDS); }
            catch (Exception exception) { throw new AssertionError(exception); }
            assertEquals(ToolStatus.DRAFT, cached.getStatus(), "Prove the managed entity is stale");
            assertThrows(InvalidStateTransitionException.class, assign ? this::assign : this::unassign);
            assertEquals(approved ? ToolStatus.PUBLISHED : ToolStatus.PENDING, cached.getStatus());
            status.setRollbackOnly();
        });
        state(approved ? "PUBLISHED" : "PENDING", 1);
        assertEquals(assign ? 0 : 1, links());
        before.remove("status"); before.remove("review_revision"); before.remove("updated_at");
        var after = snapshot(); after.keySet().retainAll(before.keySet());
        assertEquals(before, after);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void assignAndDeleteTagSerializeWithoutLosingNewAssociation(boolean assignFirst) throws Exception {
        raced(assignFirst ? this::assign : this::deleteTag, assignFirst ? this::deleteTag : this::assign,
                null, assignFirst ? CatalogConflictException.class : ResourceNotFoundException.class, false);
        assertEquals(assignFirst, tagRepository.existsById(tag));
        assertEquals(assignFirst ? 1 : 0, links()); state("DRAFT", 0);
    }

    @Test void rolledBackTagDeletionAllowsWaitingAssignment() throws Exception {
        raced(this::deleteTag, this::assign, null, null, true);
        assertTrue(tagRepository.existsById(tag)); assertEquals(1, links()); state("DRAFT", 0);
    }

    @Test void rolledBackAssignmentAllowsWaitingTagDeletion() throws Exception {
        raced(this::assign, this::deleteTag, null, null, true);
        assertFalse(tagRepository.existsById(tag)); assertEquals(0, links()); state("DRAFT", 0);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void unassignAndDeleteTagUseOneLockOrder(boolean unassignFirst) throws Exception {
        link();
        raced(unassignFirst ? this::unassign : this::deleteTag, unassignFirst ? this::deleteTag : this::unassign,
                unassignFirst ? null : CatalogConflictException.class, null, false);
        assertEquals(!unassignFirst, tagRepository.existsById(tag)); assertEquals(0, links()); state("DRAFT", 0);
    }

    /** Holds the first transaction open and proves the second backend actually waits on its lock. */
    void raced(Runnable first, Runnable second, Class<? extends Throwable> firstDenied,
               Class<? extends Throwable> secondDenied, boolean rollbackFirst) throws Exception {
        CountDownLatch ready = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger(), secondPid = new AtomicInteger();
        Future<Throwable> a = workers.submit(() -> outcome(() -> {
            TransactionTemplate tx = new TransactionTemplate(manager); tx.setTimeout(20);
            tx.executeWithoutResult(status -> {
                firstPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                if (firstDenied == null) { first.run(); em.flush(); }
                else { assertThrows(firstDenied, first::run); status.setRollbackOnly(); }
                ready.countDown(); await(release);
                if (rollbackFirst) status.setRollbackOnly();
            });
        }));
        try {
            await(ready);
            Future<Throwable> b = workers.submit(() -> outcome(() -> {
                TransactionTemplate tx = new TransactionTemplate(manager); tx.setTimeout(20);
                tx.executeWithoutResult(status -> {
                    secondPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    started.countDown(); second.run();
                });
            }));
            await(started);
            boolean blocked = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline && !b.isDone()) {
                if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class,
                        firstPid.get(), secondPid.get()))) { blocked = true; break; }
                Thread.sleep(20);
            }
            assertTrue(blocked, "Second connection must wait on shared Tool/Tag row lock");
            release.countDown(); assertNull(a.get(15, TimeUnit.SECONDS));
            Throwable result = b.get(15, TimeUnit.SECONDS);
            if (secondDenied == null) assertNull(result); else assertInstanceOf(secondDenied, result);
        } finally { release.countDown(); }
    }

    static Throwable outcome(Runnable action) { try { action.run(); return null; } catch (Throwable failure) { return failure; } }
    static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(15, TimeUnit.SECONDS), "Latch timeout"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
}
