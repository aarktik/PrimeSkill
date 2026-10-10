package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.*;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.service.PublishingService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sprint3_performance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
@Transactional
class Sprint3PerformanceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory factory;
    @Autowired PublishingService publishing;

    @Test
    void fullModerationPageDoesNotFetchEachCategorySeparately() {
        jdbc.update("insert into users(id,email,password_hash) values (9900,'performance@test.invalid','unused')");
        for (int i = 1; i <= 21; i++) {
            long id = 9900 + i;
            jdbc.update("insert into categories(id,name,slug) values (?,?,?)", id, "Category " + i, "perf-" + i);
            jdbc.update("insert into tools(id,name,slug,short_description,description,category_id,owner_id,status) "
                    + "values (?,?,?,'Short','Details',?,9900,'PENDING')", id, "Tool " + i, "perf-" + i, id);
        }
        var stats = factory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        stats.clear();
        try {
            var page = publishing.listPending(PageRequest.of(0, 20, Sort.by("id")), new CurrentActor(9900L, true));
            assertEquals(20, page.getContent().size());
            assertEquals(21, page.getTotalElements());
            assertNotNull(page.getContent().get(19).getCategoryName());
            assertTrue(stats.getPrepareStatementCount() <= 2,
                    "Expected one joined page query plus count, got " + stats.getPrepareStatementCount());
        } finally { stats.setStatisticsEnabled(wasEnabled); }
    }
}
