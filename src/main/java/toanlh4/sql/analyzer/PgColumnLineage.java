package toanlh4.sql.analyzer;

import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.avatica.util.Quoting;
import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.config.CalciteConnectionConfigImpl;
import org.apache.calcite.config.CalciteConnectionProperty;
import org.apache.calcite.plan.Contexts;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.metadata.RelColumnOrigin;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.validate.SqlConformanceEnum;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.Planner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.dbcp2.BasicDataSource;

/**
 *
 * @author toanlh4
 */
public final class PgColumnLineage {

    /**
     * PostgreSQL schemas that are never interesting for lineage.
     */
    private static final List<String> SYSTEM_SCHEMAS
            = Arrays.asList("pg_catalog", "information_schema", "pg_toast");

    /**
     * A plain DataSource. Swap in HikariCP (or any pooling DataSource) for
     * anything long-running; Calcite opens a connection per metadata read.
     */
    private static DataSource getDataSource() {
        String url = "jdbc:postgresql://localhost:5432/sbilh";
        String username = "sbilh";
        String password = "l67r8l7s6jkszetrh";
        String driver = "org.postgresql.Driver";

        BasicDataSource pgDataSource = new BasicDataSource();
        pgDataSource.setUrl(url);
        pgDataSource.setUsername(username);
        pgDataSource.setPassword(password);
        pgDataSource.setDriverClassName(driver);

//        HikariConfig config = new HikariConfig();
//        config.setJdbcUrl("jdbc:mysql://localhost:3306/your_database");
//        config.setUsername("root");
//        config.setPassword("secure_password");
//config.setReadOnly(true); // Forces all connections to be read-only
//        // Optional pool sizing configuration
//        config.setMaximumPoolSize(10);
//        config.setMinimumIdle(5);
//        config.setIdleTimeout(300000);
//        return new HikariDataSource(config);
//        DataSourceBuilder.create() // spring
//                .driverClassName(driver)
//                .url(url)
//                .username(username)
//                .password(password)
//                .build();
        return pgDataSource;
    }

    /**
     * Lists the non-system schemas in the database.
     */
    public static List<String> listSchemas(
            DataSource dataSource
    ) throws Exception {
        List<String> schemas = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getSchemas()) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_SCHEM");
                    if (!SYSTEM_SCHEMAS.contains(name) && !name.startsWith("pg_")) {
                        schemas.add(name);
                    }
                }
            }
        }
        return schemas;
    }

    /**
     * Registers each PostgreSQL schema as a Calcite sub-schema of the root.
     *
     * @param schemaNames schemas to expose; if empty, every non-system schema
     * is discovered and registered
     * @return the root schema, with sub-schemas attached
     */
    public static SchemaPlus registerPostgres(
            DataSource dataSource,
            List<String> schemaNames
    ) throws Exception {
        SchemaPlus rootSchema = Frameworks.createRootSchema(true);
        List<String> names = (schemaNames == null || schemaNames.isEmpty())
                ? listSchemas(dataSource)
                : schemaNames;

        for (String name : names) {
            // catalog = null: PostgreSQL exposes a single catalog per connection,
            // so filtering by schema alone is what you want.
            JdbcSchema jdbcSchema = JdbcSchema.create(
                    rootSchema, // parent
                    name, // name inside Calcite (mirrors the PG schema name)
                    dataSource,
                    null, // catalog
                    name // PostgreSQL schema to read
            );
            rootSchema.add(name, jdbcSchema);
        }
        return rootSchema;
    }

    /**
     * @param defaultSchemaName schema used to resolve unqualified table names,
     * normally "public"
     */
    public static FrameworkConfig buildConfig(
            SchemaPlus rootSchema,
            String defaultSchemaName
    ) {
        SchemaPlus defaultSchema = rootSchema.getSubSchema(defaultSchemaName);
        if (defaultSchema == null) {
            throw new IllegalArgumentException(
                    "schema not found: " + defaultSchemaName
                    + " (available: " + rootSchema.getSubSchemaNames() + ")");
        }

        // Match PostgreSQL's identifier rules: unquoted identifiers fold to
        // lower case, double-quoted ones keep their case, and comparison is
        // then case sensitive. This is what makes `SELECT ENAME FROM EMP`
        // resolve against a table physically named "emp".
        SqlParser.Config parserConfig = SqlParser.config()
                .withQuoting(Quoting.DOUBLE_QUOTE)
                .withUnquotedCasing(Casing.TO_LOWER)
                .withQuotedCasing(Casing.UNCHANGED)
                .withCaseSensitive(true)
                .withConformance(SqlConformanceEnum.LENIENT);
        // For PostgreSQL-specific syntax (`::` casts, etc.) add calcite-babel and:
        // .withParserFactory(org.apache.calcite.sql.parser.babel.SqlBabelParserImpl.FACTORY)

        // The catalog reader reads case sensitivity from the connection config,
        // not from the parser config, so set it in both places.
        Properties props = new Properties();
        props.setProperty(CalciteConnectionProperty.CASE_SENSITIVE.camelName(), "true");
        CalciteConnectionConfig connectionConfig = new CalciteConnectionConfigImpl(props);

        return Frameworks.newConfigBuilder()
                .parserConfig(parserConfig)
                .defaultSchema(defaultSchema)
                .context(Contexts.of(connectionConfig))
                .build();
    }

    /**
     * One source column feeding an output column.
     */
    public static final class Source {

        public final List<String> table;   // e.g. [public, orders]
        public final String column;
        public final boolean derived;      // went through an expression / aggregate?

        Source(List<String> table, String column, boolean derived) {
            this.table = table;
            this.column = column;
            this.derived = derived;
        }

        @Override
        public String toString() {
            return String.join(".", table) + "." + column + (derived ? " (derived)" : "");
        }
    }

    /**
     * Maps each output column of the SELECT to its source columns.
     *
     * {@code null} means Calcite could not determine the origin; an empty list
     * means there is no source column (a literal, for example).
     */
    public static Map<String, List<Source>> analyze(
            FrameworkConfig config,
            String sql
    ) throws Exception {
        // A Planner is single-use: build a fresh one per statement.
        try (Planner planner = Frameworks.getPlanner(config)) {
            SqlNode parsed = planner.parse(sql);
            SqlNode validated = planner.validate(parsed);
            RelRoot root = planner.rel(validated);

            // Deliberately NOT optimised. Running the JDBC rules would collapse
            // the plan into a single pushed-down JdbcTableScan and destroy the
            // per-column origins. Lineage lives on the logical plan.
            RelNode rel = root.project();

            RelMetadataQuery mq = rel.getCluster().getMetadataQuery();
            List<String> fieldNames = rel.getRowType().getFieldNames();
            Map<String, List<Source>> result = new LinkedHashMap<>();

            for (int i = 0; i < fieldNames.size(); i++) {
                Set<RelColumnOrigin> origins = mq.getColumnOrigins(rel, i);

                if (origins == null) {
                    result.put(fieldNames.get(i), null);
                    continue;
                }

                List<Source> sources = new ArrayList<>();
                for (RelColumnOrigin origin : origins) {
                    RelOptTable originTable = origin.getOriginTable();
                    String columnName = originTable.getRowType()
                            .getFieldList()
                            .get(origin.getOriginColumnOrdinal())
                            .getName();
                    sources.add(new Source(
                            originTable.getQualifiedName(),
                            columnName,
                            origin.isDerived())
                    );
                }
                result.put(fieldNames.get(i), sources);
            }

            return result;
        }
    }

    public static void main(String[] args) throws Exception {
        String defaultSchema = "stg";
        String sql = """
                    with cte_t24_fbnk_aa_bill_details as (
                        	select arrangement_id, recid, settle_status, property
                        		, bill_status, payment_indicator, bill_date
                        		, chargeoff_amount, payment_date, payment_type, os_prop_amount
                                , sys_debezium_op, sys_kafka_offset
                        	from ods_raw.t24_fbnk_aa_bill_details
                        ), cte_t24_fbnk_aa_bill_details_last_data as (
                            select arrangement_id, recid, settle_status, property
                        		, bill_status, payment_indicator
                        		, chargeoff_amount, payment_date, payment_type, os_prop_amount
                        	from (
                        		select arrangement_id, recid, settle_status, property
                        			, bill_status, payment_indicator, bill_date
                        			, chargeoff_amount, payment_date, payment_type, os_prop_amount
                        			, sys_debezium_op
                        			, row_number() over (partition by recid order by bill_date desc nulls last, sys_kafka_offset desc nulls last) as rn
                        		from cte_t24_fbnk_aa_bill_details
                        	) bb
                        	where bb.rn = 1
                                and (sys_debezium_op <> 'd' or sys_debezium_op is null)
                        ), charge_off as (
                        	select distinct arrangement_id
                        	from cte_t24_fbnk_aa_bill_details_last_data
                        	where chargeoff_amount is not null
                              and chargeoff_amount <> ''
                        ), arrangement as (
                            select arr.linked_appl_id
                                , arr.recid
                                , arr.customer
                                , arr.arrangement_id
                                , arr.product
                                , arr.product_line
                                , arr.link_type
                                , arr.currency
                                , arr.arr_status
                                , arr.co_code
                                , acl.account_number
                            from ods_raw.v_t24_fbnk_aa_arrangement_last_data arr
                            left join ods_raw.v_t24_fbnk_account_last_data acl on arr.link_arrangement = acl.arrangement_id
                            where arr.product_line = 'LENDING'
                                and arr.arr_status not in ('CLOSE', 'UNAUTH')
                        ), bill as (
                        	select b.arrangement_id, b.payment_date, b.settle_status, b.payment_indicator, b.property, b.payment_type
                        		, cast(b.os_prop_amount as float) as amt
                        	from cte_t24_fbnk_aa_bill_details_last_data b
                        	join arrangement ar on ar.arrangement_id = b.arrangement_id
                        	where b.settle_status = 'UNPAID'
                        		and b.payment_indicator = 'DEBIT'
                        ), ovdue as (
                        	select arrangement_id
                        		, sum(case when property = 'ACCOUNT' then amt else 0 end) as principle_due
                        		, sum(case when property = 'PRINCIPALINT' then amt else 0 end) as interest_due
                        		, sum(case when property = 'PENALTYINT' then amt else 0 end) as penalty_due
                        	from bill
                        	group by arrangement_id
                        ), cte_source as (
                            select com.recid AS br_code
                                , com.company_name as br_nm
                                , case
                                    when arr.product in ('COMM.OVERDRAFT.LOAN','NANO.PLUS','CA.OD.SMALL.WC.LOAN','UNSECURED.LOAN.LHPP','CA.OD.SME.WORKING.CAPITAL.LOAN')
                                        then 'Overdraft'
                                    else 'Loan'
                                end as ac_tp
                                , cast(bill.payment_date as timestamp) as txn_dtxtm
                                , arr.currency as ccy
                                , bill.payment_type as pymt_tp
                                , o.principle_due as pnp_amt
                                , o.interest_due as int_amt
                                , o.penalty_due as pny_amt
                                , ac.account_no as ac_no
                            from arrangement arr
                            join (
                                select account_number, co_code
                                    , account_no
                                from ods_raw.v_t24_fbnk_account_last_data
                                where cast(prodcat as int) between 3000 and 3230
                                    and prodcat like '^\\d+$'
                            ) ac on ac.account_number = arr.linked_appl_id
                            left join ods_raw.t24_f_company com on com.recid = ac.co_code
                            left join (
                                select arrangement_id
                                    , payment_date
                                    , bill_status
                                    , settle_status
                                    , PAYMENT_TYPE
                                from cte_t24_fbnk_aa_bill_details_last_data
                                where settle_status = 'UNPAID'
                                    and payment_date is not null
                            ) bb on bb.arrangement_id = arr.arrangement_id
                        	left join bill on bill.arrangement_id = arr.arrangement_id
                            left join ovdue o on o.arrangement_id = arr.arrangement_id
                        	left join charge_off coff on coff.arrangement_id = arr.arrangement_id
                            where coff.arrangement_id is null
                        )
                        select br_code, br_nm, ac_tp, ac_no, pymt_tp, txn_dtxtm, ccy, pnp_amt, int_amt, pny_amt
                        from cte_source
                        where txn_dtxtm is not null
                """;

        DataSource ds = getDataSource();
        SchemaPlus rootSchema = registerPostgres(ds, null);   // discover all schemas
        System.out.println("schemas: " + rootSchema.getSubSchemaNames());

        FrameworkConfig config = buildConfig(rootSchema, defaultSchema);

        System.out.println("SQL: " + sql);
        Map<String, List<Source>> lineage = analyze(config, sql);
        for (Map.Entry<String, List<Source>> e : lineage.entrySet()) {
            String rhs;
            if (e.getValue() == null) {
                rhs = "<unknown>";
            } else if (e.getValue().isEmpty()) {
                rhs = "<no source column (literal/constant)>";
            } else {
                rhs = e.getValue().toString();
            }
            System.out.printf("  %-24s <- %s%n", e.getKey(), rhs);
        }
    }
}
