package com.example.toolhub.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringJUnitConfig(ReviewAuditListenerTransactionTest.TestConfiguration.class)
class ReviewAuditListenerTransactionTest {

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private PlatformTransactionManager transactionManager;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachLogCapture() {
        logger = (Logger) LoggerFactory.getLogger(ReviewAuditListener.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachLogCapture() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void observerLogsCreatedEventOnlyAfterTransactionCommits() {
        ReviewCreatedEvent event = new ReviewCreatedEvent(101L, 3L, 7L);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        transaction.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            assertFalse(containsReviewLog(101L), "observer must wait until commit");
        });

        assertTrue(containsReviewLog(101L));
    }

    @Test
    void observerDoesNotLogCreatedEventWhenTransactionRollsBack() {
        ReviewCreatedEvent event = new ReviewCreatedEvent(102L, 3L, 7L);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        transaction.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        assertFalse(containsReviewLog(102L));
    }

    private boolean containsReviewLog(long reviewId) {
        return appender.list.stream().anyMatch(event ->
                event.getFormattedMessage().contains("review.created reviewId=" + reviewId));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(ReviewAuditListener.class)
    static class TestConfiguration {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:review-audit;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
