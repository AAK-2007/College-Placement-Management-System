package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Provides a single shared JDBC connection to the MySQL database.
 *
 * Implemented as a lazily-initialized singleton so that every DAO in the
 * application reuses the same connection instead of opening a new one per
 * query, which would be wasteful and slow.
 *
 * Update DB_URL / DB_USER / DB_PASSWORD to match your local MySQL setup.
 */
public final class DBConnection {

    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/placement_system?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_USER = "your_mysql_user";
    private static final String DB_PASSWORD = "your_password_here";

    private static Connection connection;

    // Private constructor prevents instantiation - this is a utility class.
    private DBConnection() {
    }

    /**
     * Returns a live connection, creating one the first time it is needed
     * and transparently reconnecting if the existing connection was closed.
     */
    public static Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MySQL JDBC driver not found on classpath.", e);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to connect to the database.", e);
        }
        return connection;
    }

    /** Closes the shared connection. Call once, when the application exits. */
    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error while closing database connection: " + e.getMessage());
        }
    }
}
