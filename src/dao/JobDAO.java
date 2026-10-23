package dao;

import db.DBConnection;
import model.Job;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Data Access Object for the Job entity (job openings and their eligibility criteria). */
public class JobDAO {

    public int addJob(Job j) {
        String sql = "INSERT INTO jobs (company_id, role, min_cgpa, eligible_departments, max_backlogs, "
                + "required_skills, package_lpa) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = DBConnection.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, j.getCompanyId());
            ps.setString(2, j.getRole());
            ps.setDouble(3, j.getMinCgpa());
            ps.setString(4, j.getEligibleDepartments());
            ps.setInt(5, j.getMaxBacklogs());
            ps.setString(6, j.getRequiredSkills());
            ps.setDouble(7, j.getPackageLpa());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add job: " + e.getMessage(), e);
        }
        return -1;
    }

    public Job getJobById(int jobId) {
        String sql = "SELECT * FROM jobs WHERE job_id = ?";
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch job: " + e.getMessage(), e);
        }
        return null;
    }

    public List<Job> getAllJobs() {
        List<Job> jobs = new ArrayList<>();
        String sql = "SELECT * FROM jobs ORDER BY job_id";
        try (Statement st = DBConnection.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                jobs.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch jobs: " + e.getMessage(), e);
        }
        return jobs;
    }

    public List<Job> getJobsByCompany(int companyId) {
        List<Job> jobs = new ArrayList<>();
        String sql = "SELECT * FROM jobs WHERE company_id = ? ORDER BY job_id";
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, companyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    jobs.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch jobs for company: " + e.getMessage(), e);
        }
        return jobs;
    }

    /**
     * Returns jobs whose numeric/department requirements a student already
     * satisfies - min CGPA, max backlogs, and department - evaluated in
     * MySQL. {@code FIND_IN_SET} lets one row's comma-separated
     * eligible_departments column be checked against a single department
     * value without needing to know how many departments that row lists.
     * Skill matching against the job's required_skills is applied
     * afterwards in {@code EligibilityService}, on this already-narrowed
     * result set only.
     */
    public List<Job> findJobsMatchingStudent(double studentCgpa, int studentBacklogs, String department) {
        String sql = "SELECT * FROM jobs WHERE min_cgpa <= ? AND max_backlogs >= ? "
                + "AND (eligible_departments = 'ALL' OR FIND_IN_SET(?, REPLACE(eligible_departments, ' ', '')) > 0)";
        List<Job> results = new ArrayList<>();
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement(sql)) {
            ps.setDouble(1, studentCgpa);
            ps.setInt(2, studentBacklogs);
            ps.setString(3, department.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch matching jobs: " + e.getMessage(), e);
        }
        return results;
    }

    private Job mapRow(ResultSet rs) throws SQLException {
        Job j = new Job();
        j.setJobId(rs.getInt("job_id"));
        j.setCompanyId(rs.getInt("company_id"));
        j.setRole(rs.getString("role"));
        j.setMinCgpa(rs.getDouble("min_cgpa"));
        j.setEligibleDepartments(rs.getString("eligible_departments"));
        j.setMaxBacklogs(rs.getInt("max_backlogs"));
        j.setRequiredSkills(rs.getString("required_skills"));
        j.setPackageLpa(rs.getDouble("package_lpa"));
        return j;
    }
}
