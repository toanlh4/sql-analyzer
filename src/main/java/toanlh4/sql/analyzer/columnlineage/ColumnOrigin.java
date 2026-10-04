package toanlh4.sql.analyzer.columnlineage;

import java.util.List;

/**
 * One source column feeding an output column.
 *
 * @author toanlh4
 */
public class ColumnOrigin {

    private List<String> qualifiedNames; // source table [public, orders]
    private int ordinalPosition; // 0-based index
    private String column; // source column name
    private String dataType;
    private boolean isDerived; // went through an expression / aggregate

    public ColumnOrigin() {
    }

    public ColumnOrigin(List<String> qualifiedNames, int ordinalPosition, String column, String dataType, boolean isDerived) {
        this.qualifiedNames = qualifiedNames;
        this.ordinalPosition = ordinalPosition;
        this.column = column;
        this.dataType = dataType;
        this.isDerived = isDerived;
    }

    public List<String> getQualifiedNames() {
        return qualifiedNames;
    }

    public void setQualifiedNames(List<String> qualifiedNames) {
        this.qualifiedNames = qualifiedNames;
    }

    public int getOrdinalPosition() {
        return ordinalPosition;
    }

    public void setOrdinalPosition(int ordinalPosition) {
        this.ordinalPosition = ordinalPosition;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public boolean isIsDerived() {
        return isDerived;
    }

    public void setIsDerived(boolean isDerived) {
        this.isDerived = isDerived;
    }

    @Override
    public String toString() {
        return String.format("%s.%s #%d %s %s", String.join(".", qualifiedNames), column, ordinalPosition, dataType, (isDerived ? " (derived)" : ""));
    }
}
