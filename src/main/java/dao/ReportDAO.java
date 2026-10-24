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
 * with loops, so report generation does not get slower as data grows.
 * <p>
 * Web version: each method borrows a pooled connection and closes it.
 */
public class ReportDAO {

    private final DBConnection db;

    public ReportDAO(DBConnection db) {
        this.db = db;
    }

    public long totalApplications() {
        return singleLongQuery("SELECT COUNT(*) FROM applications");
    }

    /** Number of DISTINCT students who have at least one SELECTED application (a student with two offers counts once). */
    public long totalSelected() {
        return singleLongQuery("SELECT COUNT(DISTINCT student_id) FROM applications WHERE status = 'SELECTED'");
    }

    /** Number of SELECTED applications, i.e. total offers (a student with two offers counts twice). */
    public long totalOffers() {
        return singleLongQuery("SELECT COUNT(*) FROM applications WHERE status = 'SELECTED'");
    }

    /** Web version: replaces loading every row just to count them (console used getAllStudents().size()). */
    public long totalStudents() {
        return singleLongQuery("SELECT COUNT(*) FROM students");
    }

    public long totalCompanies() {
        return singleLongQuery("SELECT COUNT(*) FROM companies");
    }

    public long totalJobs() {
        return singleLongQuery("SELECT COUNT(*) FROM jobs");
    }

    /** The headline counts of the summary report, read with a single query. */
    public record Totals(long students, long companies, long jobs, long applications, long selected, long offers) {
    }

    /**
     * Same figures as totalStudents(), totalCompanies(), totalJobs(), totalApplications(),
     * totalSelected() and totalOffers(), but computed in one database round trip.
     */
    public Totals totals() {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM students), "
                + "(SELECT COUNT(*) FROM companies), "
                + "(SELECT COUNT(*) FROM jobs), "
                + "(SELECT COUNT(*) FROM applications), "
                + "(SELECT COUNT(DISTINCT student_id) FROM applications WHERE status = 'SELECTED'), "
                + "(SELECT COUNT(*) FROM applications WHERE status = 'SELECTED')";
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return new Totals(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run count query: " + e.getMessage(), e);
        }
    }

    public double highestPackageOffered() {
        String sql = "SELECT MAX(j.package_lpa) FROM applications a "
                + "JOIN jobs j ON a.job_id = j.job_id "
                + "WHERE a.status = 'SELECTED'";
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
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

    /** Number of distinct selected students per department, computed with one GROUP BY query. */
    public Map<String, Long> selectedCountByDepartment() {
        String sql = "SELECT s.department, COUNT(DISTINCT a.student_id) AS cnt "
                + "FROM applications a "
                + "JOIN students s ON a.student_id = s.student_id "
                + "WHERE a.status = 'SELECTED' "
                + "GROUP BY s.department "
                + "ORDER BY s.department";
        return groupCountQuery(sql);
    }

    /** Number of distinct selected students per recruiting company, computed with one GROUP BY query. */
    public Map<String, Long> selectedCountByCompany() {
        String sql = "SELECT c.name, COUNT(DISTINCT a.student_id) AS cnt "
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
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run count query: " + e.getMessage(), e);
        }
    }

    private Map<String, Long> groupCountQuery(String sql) {
        Map<String, Long> result = new LinkedHashMap<>();
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
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
