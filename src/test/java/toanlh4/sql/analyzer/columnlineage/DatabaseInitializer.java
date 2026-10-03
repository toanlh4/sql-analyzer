package toanlh4.sql.analyzer.columnlineage;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author toanlh4
 */
public class DatabaseInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseInitializer.class);

    /**
     * Scans the classpath directory and runs all .sql files sorted by name.
     */
    public static void run(DataSource dataSource, String resourceDir) throws Exception {
        List<URL> sqlFiles = getSortedSqlFiles(resourceDir);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (URL fileUrl : sqlFiles) {
                    String sql = new String(fileUrl.openStream().readAllBytes());
                    executeSql(conn, sql);
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private static List<URL> getSortedSqlFiles(String resourceDir) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(resourceDir);

        List<URL> sqlFiles = new ArrayList<>();

        while (resources.hasMoreElements()) {
            URL dirUrl = resources.nextElement();
            File dir = new File(dirUrl.toURI());

            for (File f : Objects.requireNonNull(dir.listFiles())) {
                if (f.getName().endsWith(".sql")) {
                    sqlFiles.add(f.toURI().toURL());
                }
            }
        }

        sqlFiles.sort(Comparator.comparing(u -> Paths.get(u.getPath()).getFileName().toString()));
        return sqlFiles;
    }

    private static void executeSql(Connection conn, String sql) throws SQLException {
        // Split on ";" to support multi-statement files
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();

            if (!trimmed.isEmpty()) {
//                LOGGER.info("Executing SQL: " + trimmed);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(trimmed);
                }
            }
        }
    }
}
