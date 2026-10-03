package toanlh4.sql.analyzer.columnlineage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.RelConversionException;
import org.apache.calcite.tools.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 *
 * @author toanlh4
 */
@Service
public class ColumnLineageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ColumnLineageService.class);

    public ColumnLineageResponse analyze(
            ColumnLineageRequest request
    ) throws SQLException, SqlParseException, ValidationException, RelConversionException {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(request.getConnectionString());
        hikariConfig.setUsername(request.getUsername());
        hikariConfig.setPassword(request.getPassword());
        hikariConfig.setSchema(request.getSchema());

        DataSource dataSource = new HikariDataSource(hikariConfig);
        PgColumnLineage columnLineage = new PgColumnLineage(dataSource);
        SchemaPlus rootSchema = columnLineage.registerPostgres(null);

        FrameworkConfig config = columnLineage.buildConfig(rootSchema, request.getSchema());
        Map<String, List<ColumnOrigin>> lineage = columnLineage.analyze(config, request.getSql());

        return new ColumnLineageResponse(lineage);
    }
}
