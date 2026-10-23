package dao;

import db.DBConnection;
import model.Application;
import model.ApplicationStatus;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Data Access Object for the Application entity (a student applying to a job). */
public class ApplicationDAO {

    public int addApplication(Application a) {
        String sql = "INSERT INTO applications (student_id, job_id, application_date, status) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = DBConnection.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement(sql)) {
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
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, applicationId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update application status: " + e.getMessage(), e);
        }
    }

    public Application getApplicationById(int applicationId) {
        String sql = "SELECT * FROM applications WHERE application_id = ?";
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement(sql)) {
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
        try (Statement st = DBConnection.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch applications: " + e.getMessage(), e);
        }
        return list;
    }

    /** Shared helper: runs a single-parameter query and maps every row to an Application. */
    private List<Application> queryList(String sql, int param) {
        List<Application> list = new ArrayList<>();
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement(sql)) {
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
}
