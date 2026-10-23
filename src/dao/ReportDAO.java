package dao;

import db.DBConnection;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data Access Object dedicated to placement statistics.
 * <p>
 * Every method here does its aggregation with SQL (COUNT, MAX, GROUP BY)
 * instead of fetching entire tables into Java and summing/grouping them
 * with loops. For a handful of rows the difference is invisible, but the
 * approach doesn't get slower as data grows: the database can use its
 * indexes and never has to serialize rows across JDBC that the report
 * doesn't actually need to see individually.
 */
public class ReportDAO {

    public long totalApplications() {
        return singleLongQuery("SELECT COUNT(*) FROM applications");
    }

    public long totalSelected() {
        return singleLongQuery("SELECT COUNT(*) FROM applications WHERE status = 'SELECTED'");
    }

    public double highestPackageOffered() {
        String sql = "SELECT MAX(j.package_lpa) FROM applications a "
                + "JOIN jobs j ON a.job_id = j.job_id "
                + "WHERE a.status = 'SELECTED'";
        try (Statement st = DBConnection.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                double value = rs.getDouble(1);
                return rs.wasNull() ? 0.0 : value;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute highest package: " + e.getMessage(), e);
        }
        return 0.0;
    }

    /** Number of SELECTED applications per student department, computed with one GROUP BY query. */
    public Map<String, Long> selectedCountByDepartment() {
        String sql = "SELECT s.department, COUNT(*) AS cnt "
                + "FROM applications a "
                + "JOIN students s ON a.student_id = s.student_id "
                + "WHERE a.status = 'SELECTED' "
                + "GROUP BY s.department "
                + "ORDER BY s.department";
        return groupCountQuery(sql);
    }

    /** Number of SELECTED applications per recruiting company, computed with one GROUP BY query. */
    public Map<String, Long> selectedCountByCompany() {
        String sql = "SELECT c.name, COUNT(*) AS cnt "
                + "FROM applications a "
                + "JOIN jobs j ON a.job_id = j.job_id "
                + "JOIN companies c ON j.company_id = c.company_id "
                + "WHERE a.status = 'SELECTED' "
                + "GROUP BY c.name "
                + "ORDER BY c.name";
        return groupCountQuery(sql);
    }

    // ---- shared helpers ----

    private long singleLongQuery(String sql) {
        try (Statement st = DBConnection.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run count query: " + e.getMessage(), e);
        }
    }

    private Map<String, Long> groupCountQuery(String sql) {
        Map<String, Long> result = new LinkedHashMap<>();
        try (Statement st = DBConnection.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.put(rs.getString(1), rs.getLong(2));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run group-by query: " + e.getMessage(), e);
        }
        return result;
    }
}
