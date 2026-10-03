package toanlh4.sql.analyzer.columnlineage;

import java.util.List;

/**
 *
 * @author toanlh4
 */
public class ColumnLineage {
    
    private int targetColumnOrdinalPosition;
    private String targetColumn;
    private List<ColumnOrigin> columnOrigins;

    public int getTargetColumnOrdinalPosition() {
        return targetColumnOrdinalPosition;
    }

    public void setTargetColumnOrdinalPosition(int targetColumnOrdinalPosition) {
        this.targetColumnOrdinalPosition = targetColumnOrdinalPosition;
    }

    public String getTargetColumn() {
        return targetColumn;
    }

    public void setTargetColumn(String targetColumn) {
        this.targetColumn = targetColumn;
    }

    public List<ColumnOrigin> getColumnOrigins() {
        return columnOrigins;
    }

    public void setColumnOrigins(List<ColumnOrigin> columnOrigins) {
        this.columnOrigins = columnOrigins;
    }

}
