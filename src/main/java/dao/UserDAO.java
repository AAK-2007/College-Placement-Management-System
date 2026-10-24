package dao;

import db.DBConnection;

import java.sql.*;

/**
 * Data Access Object for login accounts (the users table).
 * A STUDENT account is linked to one students row, a COMPANY account to one companies row.
 * Passwords are stored only as BCrypt hashes.
 */
public class UserDAO {

    /** One account, with the linked student's or company's name for display. */
    public record UserRecord(int userId, String username, String passwordHash, String role,
                             Integer studentId, Integer companyId, String displayName) {
    }

    private final DBConnection db;

    public UserDAO(DBConnection db) {
        this.db = db;
    }

    public UserRecord findByUsername(String username) {
        String sql = "SELECT u.user_id, u.username, u.password_hash, u.role, u.student_id, u.company_id, "
                + "COALESCE(s.name, c.name) AS display_name "
                + "FROM users u "
                + "LEFT JOIN students s ON u.student_id = s.student_id "
                + "LEFT JOIN companies c ON u.company_id = c.company_id "
                + "WHERE u.username = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new UserRecord(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("role"),
                        rs.getObject("student_id", Integer.class),
                        rs.getObject("company_id", Integer.class),
                        rs.getString("display_name"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch user: " + e.getMessage(), e);
        }
    }

    public boolean existsByUsername(String username) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check username: " + e.getMessage(), e);
        }
    }

    /** Stores a new account. Exactly one of studentId / companyId is set. Returns the new user id. */
    public int addUser(String username, String passwordHash, String role, Integer studentId, Integer companyId) {
        String sql = "INSERT INTO users (username, password_hash, role, student_id, company_id) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, role);
            ps.setObject(4, studentId, Types.INTEGER);
            ps.setObject(5, companyId, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add user: " + e.getMessage(), e);
        }
    }
}
