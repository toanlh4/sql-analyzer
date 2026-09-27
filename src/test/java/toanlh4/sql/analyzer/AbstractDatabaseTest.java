package toanlh4.sql.analyzer;

import javax.sql.DataSource;

/**
 *
 * @author toanlh4
 */
public abstract class AbstractDatabaseTest {

    protected abstract DataSource getDataSource();

    protected abstract String getDdlResourcePath(); // "db/postgres"

}
