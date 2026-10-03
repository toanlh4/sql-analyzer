package toanlh4.sql.analyzer.columnlineage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author toanlh4
 */
public class ColumnLineageResponse {

    private final List<ColumnLineage> columnLineages;

    public ColumnLineageResponse(Map<String, List<ColumnOrigin>> lineage) {
        columnLineages = fromLineage(lineage);
    }

    private List<ColumnLineage> fromLineage(Map<String, List<ColumnOrigin>> lineage) {
        List<ColumnLineage> ret = new ArrayList<>();
        int i = 0;
        
        for (Map.Entry<String, List<ColumnOrigin>> entry : lineage.entrySet()) {
            ColumnLineage columnLineage = new ColumnLineage();

            columnLineage.setTargetColumnOrdinalPosition(i + 1);
            columnLineage.setTargetColumn(entry.getKey());
            columnLineage.setColumnOrigins(entry.getValue());
            ret.add(columnLineage);

            i++;
        }
        
        return ret;
    }

    public List<ColumnLineage> getColumnLineages() {
        return columnLineages;
    }
}
