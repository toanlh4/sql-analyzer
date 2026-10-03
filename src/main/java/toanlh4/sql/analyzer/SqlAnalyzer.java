package toanlh4.sql.analyzer;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.tools.FrameworkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author toanlh4
 */
public class SqlAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlAnalyzer.class);

    private static final String SCHEMA_TEST = "sample";

    private static DataSource getDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/sql_analyzer");
        config.setUsername("postgres");
        config.setPassword("postgres");
        config.setSchema("sample");
        return new HikariDataSource(config);
    }

    public static void main(String[] args) throws Exception {
        DataSource dataSource = getDataSource();
        PgColumnLineage columnLineage = new PgColumnLineage(dataSource);
        
        SchemaPlus rootSchema = columnLineage.registerPostgres(null);

        String sql = """
            select o.order_id
                    , c.name as customer_name
                    , p.name as product_name
                    , oi.quantity
                    , oi.unit_price
                    , oi.discount
                    , (oi.quantity * oi.unit_price * (1 - oi.discount / 100)) as line_total
                from order_items oi
                join orders o on o.order_id = oi.order_id
                join customers c on c.customer_id = o.customer_id
                join products p on p.product_id = oi.product_id""";

        FrameworkConfig config = columnLineage.buildConfig(rootSchema, SCHEMA_TEST);
        Map<String, List<Source>> lineage = columnLineage.analyze(config, sql);
       
        for (Map.Entry<String, List<Source>> e : lineage.entrySet()) {
            String rhs;
            
            if (e.getValue() == null) {
                rhs = "<unknown>";
            } else if (e.getValue().isEmpty()) {
                rhs = "<no source column (literal/constant)>";
            } else {
                rhs = e.getValue().toString();
            }
            
            LOGGER.info(String.format("  %-24s <- %s", e.getKey(), rhs));
        }
    }
}
