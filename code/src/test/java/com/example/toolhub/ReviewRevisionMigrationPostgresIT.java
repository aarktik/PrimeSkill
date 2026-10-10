package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.*;
import com.example.toolhub.support.PostgresTestDatabaseGuard;
import java.nio.file.*;
import java.sql.*;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;

@SpringBootTest(properties="spring.datasource.url=${PRIMESKILL_TEST_DB_URL}")
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers=PostgresTestDatabaseGuard.class)
class ReviewRevisionMigrationPostgresIT {
    @Autowired DataSource source;
    Connection connection;
    String schema;
    @BeforeEach void fixture()throws Exception{
        connection=source.getConnection();schema="b1_migration_"+UUID.randomUUID().toString().replace("-","");
        sql("create schema "+schema);sql("set search_path to "+schema);
        sql("create table tools(id bigint primary key, name text not null, status varchar(30) not null)");
    }
    @AfterEach void cleanup()throws Exception{
        if(connection!=null)try(var c=connection){sql("rollback");c.setAutoCommit(true);sql("set search_path to public");sql("drop schema "+schema+" cascade");}
    }
    void sql(String value)throws SQLException{try(var s=connection.createStatement()){s.execute(value);}}
    String value(String query)throws SQLException{try(var s=connection.createStatement();var r=s.executeQuery(query)){assertTrue(r.next());return r.getString(1);}}
    void migrate()throws Exception{
        String migration=Files.readString(Path.of("../doc/sql/drafts/B1__add_tool_review_revision.sql"));
        sql(migration);
    }
    @Test void emptyTableAndPopulatedLegacyRowsSurviveRerun()throws Exception{
        migrate();
        sql("insert into tools(id,name,status) values(1,'Keep','PENDING')");
        migrate();
        assertEquals("Keep:PENDING:0",value("select name||':'||status||':'||review_revision from tools where id=1"));
        assertThrows(SQLException.class,()->sql("update tools set review_revision=-1 where id=1"));
        assertThrows(SQLException.class,()->sql("update tools set review_revision=null where id=1"));
        assertEquals("0",value("select review_revision from tools where id=1"));
    }
    @Test void populatedLegacyPendingGetsZeroWithoutChangingContent()throws Exception{
        sql("insert into tools values(1,'Existing','PENDING'),(2,'Draft','DRAFT')");migrate();
        assertEquals("2",value("select count(*) from tools where review_revision=0"));
        assertEquals("Existing:PENDING",value("select name||':'||status from tools where id=1"));
    }
    @ParameterizedTest @ValueSource(strings={
        "review_revision integer not null default 0",
        "review_revision bigint default 0",
        "review_revision bigint not null default 1",
        "review_revision bigint not null default 0 constraint ck_tools_review_revision_nonnegative check(review_revision>=-1)"})
    void incompatibleExistingColumnOrConstraintFailsWithoutMutation(String declaration)throws Exception{
        sql("alter table tools add column "+declaration);sql("insert into tools(id,name,status) values(1,'Keep','PENDING')");
        assertThrows(SQLException.class,()->migrate());
        // A script ending in a failed explicit transaction must be rolled back by its caller.
        sql("rollback");assertEquals("Keep:PENDING",value("select name||':'||status from tools where id=1"));
    }
}
