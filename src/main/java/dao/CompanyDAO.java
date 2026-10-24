package dao;

import db.DBConnection;
import model.Company;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for the Company entity.
 * Web version: each method borrows a pooled connection and closes it.
 */
public class CompanyDAO {

    private final DBConnection db;

    public CompanyDAO(DBConnection db) {
        this.db = db;
    }

    public int addCompany(Company c) {
        String sql = "INSERT INTO companies (name, sector, contact_email, contact_phone) VALUES (?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getName());
            ps.setString(2, c.getSector());
            ps.setString(3, c.getContactEmail());
            ps.setString(4, c.getContactPhone());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add company: " + e.getMessage(), e);
        }
        return -1;
    }

    public Company getCompanyById(int companyId) {
        String sql = "SELECT * FROM companies WHERE company_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, companyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch company: " + e.getMessage(), e);
        }
        return null;
    }

    public List<Company> getAllCompanies() {
        List<Company> companies = new ArrayList<>();
        String sql = "SELECT * FROM companies ORDER BY company_id";
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                companies.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch companies: " + e.getMessage(), e);
        }
        return companies;
    }

    /** Company id -> name, for showing names next to jobs. Reads only the two columns it needs. */
    public Map<Integer, String> getCompanyNames() {
        Map<Integer, String> names = new HashMap<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT company_id, name FROM companies")) {
            while (rs.next()) {
                names.put(rs.getInt(1), rs.getString(2));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch company names: " + e.getMessage(), e);
        }
        return names;
    }

    /** Deletes the company; ON DELETE CASCADE removes its jobs, their applications and interviews. */
    public boolean deleteCompany(int companyId) {
        String sql = "DELETE FROM companies WHERE company_id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, companyId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete company: " + e.getMessage(), e);
        }
    }

    // ---- Counts shown on the delete confirmation page ----

    public long countJobs(int companyId) {
        return countFor("SELECT COUNT(*) FROM jobs WHERE company_id = ?", companyId);
    }

    public long countApplications(int companyId) {
        return countFor("SELECT COUNT(*) FROM applications a JOIN jobs j ON a.job_id = j.job_id "
                + "WHERE j.company_id = ?", companyId);
    }

    public long countInterviews(int companyId) {
        return countFor("SELECT COUNT(*) FROM interviews i "
                + "JOIN applications a ON i.application_id = a.application_id "
                + "JOIN jobs j ON a.job_id = j.job_id WHERE j.company_id = ?", companyId);
    }

    private long countFor(String sql, int id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count related records: " + e.getMessage(), e);
        }
    }

    private Company mapRow(ResultSet rs) throws SQLException {
        Company c = new Company();
        c.setCompanyId(rs.getInt("company_id"));
        c.setName(rs.getString("name"));
        c.setSector(rs.getString("sector"));
        c.setContactEmail(rs.getString("contact_email"));
        c.setContactPhone(rs.getString("contact_phone"));
        return c;
    }
}
