package toanlh4.sql.analyzer;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static toanlh4.sql.analyzer.PgColumnLineageTest.POSTGRES;

/**
 *
 * @author toanlh4
 */
@Testcontainers
public class PostgresTestBase extends AbstractDatabaseTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresTestBase.class);

    static final String SCHEMA_TEST = "sample";
    
    // Shared across all subclass tests (started once)
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.2-alpine3.19")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    DataSource dataSource;

    @Override
    protected DataSource getDataSource() {
        return dataSource;
    }

    @Override
    protected String getDdlResourcePath() {
        return "db/postgres";
    }

    @BeforeAll
    static void startContainer() {
        LOGGER.info("Starting container");
        try {
            POSTGRES.start();
        } catch (Exception ex) {
            LOGGER.error("", ex);
        }
    }

    @BeforeEach
    void setupDataSource() {
        LOGGER.info("Creating connection");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setSchema(SCHEMA_TEST);
        dataSource = new HikariDataSource(config);

        LOGGER.info("Initializing schema");
        try {
            DatabaseInitializer.run(dataSource, getDdlResourcePath());
        } catch (Exception ex) {
            LOGGER.error("", ex);
        }
    }

}
