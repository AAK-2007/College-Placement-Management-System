package dao;

import db.DBConnection;
import model.Company;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Data Access Object for the Company entity. */
public class CompanyDAO {

    public int addCompany(Company c) {
        String sql = "INSERT INTO companies (name, sector, contact_email, contact_phone) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = DBConnection.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement(sql)) {
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
        try (Statement st = DBConnection.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                companies.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch companies: " + e.getMessage(), e);
        }
        return companies;
    }

    public boolean deleteCompany(int companyId) {
        String sql = "DELETE FROM companies WHERE company_id=?";
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, companyId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete company: " + e.getMessage(), e);
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
