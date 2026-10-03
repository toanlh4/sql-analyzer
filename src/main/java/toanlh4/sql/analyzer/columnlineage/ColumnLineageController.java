package toanlh4.sql.analyzer.columnlineage;

import java.sql.SQLException;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.tools.RelConversionException;
import org.apache.calcite.tools.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author toanlh4
 */
@RestController
@RequestMapping({
    "/api/analyze-sql/column-lineage"
})
public class ColumnLineageController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ColumnLineageController.class);

    private final ColumnLineageService columnLineageService;

    public ColumnLineageController(ColumnLineageService columnLineageService) {
        this.columnLineageService = columnLineageService;
    }

    @PostMapping("")
    public ResponseEntity<ColumnLineageResponse> findColumnsUsed(
            @RequestBody ColumnLineageRequest request
    ) throws SQLException, SqlParseException, ValidationException, RelConversionException {
        ColumnLineageResponse ret = columnLineageService.analyze(request);
        return ResponseEntity.ok().body(ret);
    }
}
