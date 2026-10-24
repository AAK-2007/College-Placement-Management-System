package dao;

import db.DBConnection;
import model.Interview;
import model.InterviewResult;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the Interview entity.
 * Web version: each method borrows a pooled connection and closes it.
 */
public class InterviewDAO {

    private final DBConnection db;

    public InterviewDAO(DBConnection db) {
        this.db = db;
    }

    public int addInterview(Interview i) {
        String sql = "INSERT INTO interviews (application_id, interview_date, round, result, remarks) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, i.getApplicationId());
            ps.setDate(2, Date.valueOf(i.getInterviewDate()));
            ps.setString(3, i.getRound());
            ps.setString(4, i.getResult().name());
            ps.setString(5, i.getRemarks());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add interview: " + e.getMessage(), e);
        }
        return -1;
    }

    /** Needed by the web version to find which application a round belongs to before recording a result. */
    public Interview getInterviewById(int interviewId) {
        String sql = "SELECT * FROM interviews WHERE interview_id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, interviewId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch interview: " + e.getMessage(), e);
        }
    }

    public List<Interview> getInterviewsByApplication(int applicationId) {
        List<Interview> list = new ArrayList<>();
        String sql = "SELECT * FROM interviews WHERE application_id = ? ORDER BY interview_date, interview_id";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch interviews: " + e.getMessage(), e);
        }
        return list;
    }

    public boolean updateResult(int interviewId, InterviewResult result, String remarks) {
        String sql = "UPDATE interviews SET result = ?, remarks = ? WHERE interview_id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, result.name());
            ps.setString(2, remarks);
            ps.setInt(3, interviewId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update interview result: " + e.getMessage(), e);
        }
    }

    private Interview mapRow(ResultSet rs) throws SQLException {
        Interview i = new Interview();
        i.setInterviewId(rs.getInt("interview_id"));
        i.setApplicationId(rs.getInt("application_id"));
        Date d = rs.getDate("interview_date");
        i.setInterviewDate(d != null ? d.toLocalDate() : LocalDate.now());
        i.setRound(rs.getString("round"));
        i.setResult(InterviewResult.valueOf(rs.getString("result")));
        i.setRemarks(rs.getString("remarks"));
        return i;
    }
}
