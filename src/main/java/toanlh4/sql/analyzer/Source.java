package toanlh4.sql.analyzer;

import java.util.List;

/**
 * One source column feeding an output column.
 *
 * @author toanlh4
 */
public class Source {

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
