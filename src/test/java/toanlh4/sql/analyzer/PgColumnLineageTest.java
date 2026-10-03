package toanlh4.sql.analyzer;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.tools.FrameworkConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author toanlh4
 */
@TestInstance(Lifecycle.PER_CLASS)
public class PgColumnLineageTest extends PostgresTestBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(PgColumnLineageTest.class);

    static Stream<Arguments> providePostgresSqls() {
        return Stream.of(
                Arguments.of("""
                    select c.customer_id
                            , c.name as customer_name
                            , c.tier
                            , o.order_id
                            , o.status
                            , o.total_amount
                            , o.ordered_at
                        from customers c
                        join orders o on o.customer_id = c.customer_id""", true),
                Arguments.of("""
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
                        join products p on p.product_id = oi.product_id""", true)
        );
    }

    @ParameterizedTest
    @MethodSource("providePostgresSqls")
    void testSimple(String sql, boolean expectedResult) throws Exception {
        PgColumnLineage columnLineage = new PgColumnLineage(dataSource);
        
        SchemaPlus rootSchema = columnLineage.registerPostgres(null);   // discover all schemas
        FrameworkConfig config = columnLineage.buildConfig(rootSchema, SCHEMA_TEST);
        Map<String, List<Source>> lineage = columnLineage.analyze(config, sql);

        LOGGER.info("SQL: " + sql);
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

        Assertions.assertTrue(expectedResult);
    }
}
