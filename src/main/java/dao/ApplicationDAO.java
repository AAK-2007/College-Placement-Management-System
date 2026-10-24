package dao;

import db.DBConnection;
import model.Application;
import model.ApplicationStatus;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the Application entity (a student applying to a job).
 * Web version: each method borrows a pooled connection and closes it.
 */
public class ApplicationDAO {

    /**
     * An application joined with the student, job and company names, for display in tables.
     * Read-only: the business rules still work with {@link Application}.
     */
    public record ApplicationView(int applicationId, int studentId, String studentName, String registerNumber,
                                  int jobId, int companyId, String companyName, String role,
                                  LocalDate applicationDate, ApplicationStatus status) {
    }

    private static final String VIEW_SELECT =
            "SELECT a.application_id, a.student_id, s.name AS student_name, s.register_number, "
                    + "a.job_id, j.company_id, c.name AS company_name, j.role, a.application_date, a.status "
                    + "FROM applications a "
                    + "JOIN students s ON a.student_id = s.student_id "
                    + "JOIN jobs j ON a.job_id = j.job_id "
                    + "JOIN companies c ON j.company_id = c.company_id ";

    private final DBConnection db;

    public ApplicationDAO(DBConnection db) {
        this.db = db;
    }

    public int addApplication(Application a) {
        String sql = "INSERT INTO applications (student_id, job_id, application_date, status) VALUES (?, ?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, a.getStudentId());
            ps.setInt(2, a.getJobId());
            ps.setDate(3, Date.valueOf(a.getApplicationDate()));
            ps.setString(4, a.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add application: " + e.getMessage(), e);
        }
        return -1;
    }

    /** Returns true if the student has already applied to this job (used to prevent duplicates). */
    public boolean hasApplied(int studentId, int jobId) {
        String sql = "SELECT 1 FROM applications WHERE student_id = ? AND job_id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check existing application: " + e.getMessage(), e);
        }
    }

    public boolean updateStatus(int applicationId, ApplicationStatus status) {
        String sql = "UPDATE applications SET status = ? WHERE application_id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, applicationId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update application status: " + e.getMessage(), e);
        }
    }

    public Application getApplicationById(int applicationId) {
        String sql = "SELECT * FROM applications WHERE application_id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch application: " + e.getMessage(), e);
        }
        return null;
    }

    public List<Application> getApplicationsByStudent(int studentId) {
        return queryList("SELECT * FROM applications WHERE student_id = ? ORDER BY application_id", studentId);
    }

    public List<Application> getApplicationsByJob(int jobId) {
        return queryList("SELECT * FROM applications WHERE job_id = ? ORDER BY application_id", jobId);
    }

    public List<Application> getAllApplications() {
        List<Application> list = new ArrayList<>();
        String sql = "SELECT * FROM applications ORDER BY application_id";
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch applications: " + e.getMessage(), e);
        }
        return list;
    }

    // ---- Display queries (names instead of ids) ----

    /** All applications, or only one student's (studentId) or one job's (jobId) when given. */
    public List<ApplicationView> getApplicationViews(Integer studentId, Integer jobId) {
        return getApplicationViews(studentId, jobId, null);
    }

    /**
     * Applications matching every filter that is not null: one student, one job, and/or
     * one company (applications to that company's jobs).
     */
    public List<ApplicationView> getApplicationViews(Integer studentId, Integer jobId, Integer companyId) {
        StringBuilder sql = new StringBuilder(VIEW_SELECT);
        List<Integer> params = new ArrayList<>();
        if (studentId != null) {
            sql.append(params.isEmpty() ? "WHERE " : "AND ").append("a.student_id = ? ");
            params.add(studentId);
        }
        if (jobId != null) {
            sql.append(params.isEmpty() ? "WHERE " : "AND ").append("a.job_id = ? ");
            params.add(jobId);
        }
        if (companyId != null) {
            sql.append(params.isEmpty() ? "WHERE " : "AND ").append("j.company_id = ? ");
            params.add(companyId);
        }
        sql.append("ORDER BY a.application_id");

        List<ApplicationView> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setInt(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapView(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch applications: " + e.getMessage(), e);
        }
        return list;
    }

    public ApplicationView getApplicationView(int applicationId) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(VIEW_SELECT + "WHERE a.application_id = ?")) {
            ps.setInt(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapView(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch application: " + e.getMessage(), e);
        }
    }

    /** Shared helper: runs a single-parameter query and maps every row to an Application. */
    private List<Application> queryList(String sql, int param) {
        List<Application> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch applications: " + e.getMessage(), e);
        }
        return list;
    }

    private Application mapRow(ResultSet rs) throws SQLException {
        Application a = new Application();
        a.setApplicationId(rs.getInt("application_id"));
        a.setStudentId(rs.getInt("student_id"));
        a.setJobId(rs.getInt("job_id"));
        Date d = rs.getDate("application_date");
        a.setApplicationDate(d != null ? d.toLocalDate() : LocalDate.now());
        a.setStatus(ApplicationStatus.valueOf(rs.getString("status")));
        return a;
    }

    private ApplicationView mapView(ResultSet rs) throws SQLException {
        Date d = rs.getDate("application_date");
        return new ApplicationView(
                rs.getInt("application_id"),
                rs.getInt("student_id"),
                rs.getString("student_name"),
                rs.getString("register_number"),
                rs.getInt("job_id"),
                rs.getInt("company_id"),
                rs.getString("company_name"),
                rs.getString("role"),
                d != null ? d.toLocalDate() : null,
                ApplicationStatus.valueOf(rs.getString("status")));
    }
}
