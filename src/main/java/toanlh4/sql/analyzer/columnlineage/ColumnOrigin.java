package toanlh4.sql.analyzer.columnlineage;

import java.util.List;

/**
 * One source column feeding an output column.
 *
 * @author toanlh4
 */
public class ColumnOrigin {

    private List<String> qualifiedNames; // [public, orders]
    private String column;
    private boolean isDerived; // went through an expression / aggregate

    public ColumnOrigin() {
    }

    public ColumnOrigin(List<String> qualifiedNames, String column, boolean isDerived) {
        this.qualifiedNames = qualifiedNames;
        this.column = column;
        this.isDerived = isDerived;
    }

    public List<String> getQualifiedNames() {
        return qualifiedNames;
    }

    public void setQualifiedNames(List<String> qualifiedNames) {
        this.qualifiedNames = qualifiedNames;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public boolean isIsDerived() {
        return isDerived;
    }

    public void setIsDerived(boolean isDerived) {
        this.isDerived = isDerived;
    }

    @Override
    public String toString() {
        return String.join(".", qualifiedNames) + "." + column + (isDerived ? " (derived)" : "");
    }
}
