package db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hands out JDBC connections from the connection pool.
 * <p>
 * The console version kept one shared connection for the whole program. A web
 * application serves many requests at the same time, so this version wraps a
 * pooled {@link DataSource} (HikariCP, configured by Spring Boot from
 * application.properties). Every DAO call borrows a connection, uses it, and
 * closes it in a try-with-resources block, which returns it to the pool.
 */
public class DBConnection {

    private final DataSource dataSource;

    public DBConnection(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Borrows a connection from the pool. The caller must close it (try-with-resources). */
    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to connect to the database.", e);
        }
    }
}
