package toanlh4.sql.analyzer.columnlineage;

/**
 *
 * @author toanlh4
 */
public class ColumnLineageRequest {
    
    private String sql;
    private String connectionString;
    private String username;
    private String password;
    private String schema;

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ColumnLineageRequest{");
        sb.append("sql=").append(sql);
        sb.append(", connectionString=").append(connectionString);
        sb.append(", username=").append(username);
        sb.append(", password=").append(password);
        sb.append(", schema=").append(schema);
        sb.append('}');
        return sb.toString();
    }
    
}
