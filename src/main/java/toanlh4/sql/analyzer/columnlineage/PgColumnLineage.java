package toanlh4.sql.analyzer.columnlineage;

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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.tools.RelConversionException;
import org.apache.calcite.tools.ValidationException;

/**
 *
 * @author toanlh4
 */
public final class PgColumnLineage {

    private final DataSource dataSource;

    public PgColumnLineage(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * PostgreSQL schemas that are never interesting for lineage.
     */
    private static final List<String> SYSTEM_SCHEMAS
            = Arrays.asList("pg_catalog", "information_schema", "pg_toast");

    /**
     * Lists the non-system schemas in the database.
     *
     * @return
     * @throws java.sql.SQLException
     */
    public List<String> listSchemas() throws SQLException {
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
     * @throws java.sql.SQLException
     */
    public SchemaPlus registerPostgres(
            List<String> schemaNames
    ) throws SQLException {
        SchemaPlus rootSchema = Frameworks.createRootSchema(true);
        List<String> names = (schemaNames == null || schemaNames.isEmpty())
                ? listSchemas()
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
     * @param rootSchema
     * @param defaultSchemaName schema used to resolve unqualified table names,
     * normally "public"
     * @return
     */
    public FrameworkConfig buildConfig(
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
     * Maps each output column of the SELECT to its source columns.
     *
     * {@code null} means Calcite could not determine the origin; an empty list
     * means there is no source column (a literal, for example).
     *
     * @param config
     * @param sql
     * @return
     * @throws org.apache.calcite.sql.parser.SqlParseException
     * @throws org.apache.calcite.tools.ValidationException
     * @throws org.apache.calcite.tools.RelConversionException
     */
    public Map<String, List<ColumnOrigin>> analyze(
            FrameworkConfig config,
            String sql
    ) throws SqlParseException, ValidationException, RelConversionException {
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
            List<RelDataTypeField> fields = rel.getRowType().getFieldList();
            Map<String, List<ColumnOrigin>> result = new LinkedHashMap<>();

            for (int i = 0; i < fields.size(); i++) {
                Set<RelColumnOrigin> origins = mq.getColumnOrigins(rel, i);
                RelDataTypeField field = fields.get(i);
                String fieldName = field.getName();
                String dataType = field.getType().getDigest().getDigestString();
                

                if (origins == null) {
                    result.put(fieldName, null);
                    continue;
                }

                List<ColumnOrigin> columnOrigins = new ArrayList<>();
                for (RelColumnOrigin origin : origins) {
                    RelOptTable originTable = origin.getOriginTable();
                    String columnName = originTable.getRowType()
                            .getFieldList()
                            .get(origin.getOriginColumnOrdinal())
                            .getName();
                    columnOrigins.add(new ColumnOrigin(
                            originTable.getQualifiedName(),
                            field.getIndex(),
                            columnName,
                            dataType,
                            origin.isDerived())
                    );
                }
                result.put(fieldName, columnOrigins);
            }

            return result;
        }
    }
}
