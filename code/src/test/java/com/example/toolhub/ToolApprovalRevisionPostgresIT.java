package com.example.toolhub;

import com.example.toolhub.support.PostgresTestDatabaseGuard;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties="spring.datasource.url=${PRIMESKILL_TEST_DB_URL}")
@ActiveProfiles(profiles="postgres-test",inheritProfiles=false)
@ContextConfiguration(initializers=PostgresTestDatabaseGuard.class)
class ToolApprovalRevisionPostgresIT extends ReviewDecisionIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired org.springframework.transaction.PlatformTransactionManager manager;

    @org.junit.jupiter.api.Test
    void actualDatabaseLockTimeoutReturns503WithoutPartialDecisionAndPoolRecovers() throws Exception {
        submit();
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        var ready=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var blocker=pool.submit(()->new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(s->{
            tools.findForUpdateById(tool).orElseThrow();ready.countDown();
            ToolApprovalConcurrencyPostgresIT.await(release);
        }));
        try {
            ToolApprovalConcurrencyPostgresIT.await(ready);
            new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(s->{
                jdbc.execute("set local lock_timeout='100ms'");
                try { decision("approve","{\"expectedReviewRevision\":1}")
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("CONCURRENT_OPERATION_RETRY"));
                } catch(Exception e){throw new AssertionError(e);} finally{s.setRollbackOnly();}
            });
            org.junit.jupiter.api.Assertions.assertEquals("PENDING",state());
        } finally {
            release.countDown();blocker.get(15,java.util.concurrent.TimeUnit.SECONDS);
            pool.shutdownNow();org.junit.jupiter.api.Assertions.assertTrue(pool.awaitTermination(15,java.util.concurrent.TimeUnit.SECONDS));
        }
        decision("approve","{\"expectedReviewRevision\":1}")
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }
}
