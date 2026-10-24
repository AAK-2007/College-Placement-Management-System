package dao;

import db.DBConnection;
import model.Student;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the Student entity.
 * Isolates all SQL for students in one place so the rest of the
 * application never writes raw SQL directly - it only calls these methods.
 * All queries use PreparedStatement to prevent SQL injection.
 * <p>
 * Web version: each method borrows a connection from the pool and closes it
 * (returns it to the pool) in try-with-resources. SQL is unchanged.
 */
public class StudentDAO {

    private final DBConnection db;

    public StudentDAO(DBConnection db) {
        this.db = db;
    }

    public int addStudent(Student s) {
        String sql = "INSERT INTO students (register_number, name, department, cgpa, backlogs, skills, email, phone) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getRegisterNumber());
            ps.setString(2, s.getName());
            ps.setString(3, s.getDepartment());
            ps.setDouble(4, s.getCgpa());
            ps.setInt(5, s.getBacklogs());
            ps.setString(6, s.getSkills());
            ps.setString(7, s.getEmail());
            ps.setString(8, s.getPhone());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add student: " + e.getMessage(), e);
        }
        return -1;
    }

    /** Returns true if a student with this register number is already stored (the column is UNIQUE). */
    public boolean existsByRegisterNumber(String registerNumber) {
        String sql = "SELECT 1 FROM students WHERE register_number = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, registerNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check register number: " + e.getMessage(), e);
        }
    }

    public Student getStudentById(int studentId) {
        String sql = "SELECT * FROM students WHERE student_id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch student: " + e.getMessage(), e);
        }
        return null;
    }

    public List<Student> getAllStudents() {
        List<Student> students = new ArrayList<>();
        String sql = "SELECT * FROM students ORDER BY student_id";
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                students.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch students: " + e.getMessage(), e);
        }
        return students;
    }

    public boolean updateStudent(Student s) {
        String sql = "UPDATE students SET name=?, department=?, cgpa=?, backlogs=?, skills=?, email=?, phone=? "
                + "WHERE student_id=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getDepartment());
            ps.setDouble(3, s.getCgpa());
            ps.setInt(4, s.getBacklogs());
            ps.setString(5, s.getSkills());
            ps.setString(6, s.getEmail());
            ps.setString(7, s.getPhone());
            ps.setInt(8, s.getStudentId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update student: " + e.getMessage(), e);
        }
    }

    /** Deletes the student; ON DELETE CASCADE in schema.sql removes their applications and interviews. */
    public boolean deleteStudent(int studentId) {
        String sql = "DELETE FROM students WHERE student_id=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete student: " + e.getMessage(), e);
        }
    }

    /** How many applications a delete of this student would also remove (for the confirmation page). */
    public long countApplications(int studentId) {
        return countFor("SELECT COUNT(*) FROM applications WHERE student_id = ?", studentId);
    }

    /** How many interview rounds a delete of this student would also remove (for the confirmation page). */
    public long countInterviews(int studentId) {
        return countFor("SELECT COUNT(*) FROM interviews i JOIN applications a ON i.application_id = a.application_id "
                + "WHERE a.student_id = ?", studentId);
    }

    /**
     * Returns students that pass the numeric/department side of a job's
     * eligibility criteria - CGPA, backlog ceiling, and department - all
     * evaluated by MySQL itself via indexed/typed comparisons instead of
     * pulling the whole table into Java and filtering there.
     * <p>
     * Skill matching is deliberately left out of this query: skills are
     * stored as a free-form comma-separated string, so it cannot be
     * expressed as a clean, safe SQL predicate without normalizing the
     * schema. It is applied afterwards, in {@code EligibilityService}, but
     * only against this already-small candidate set.
     *
     * @param minCgpa            minimum CGPA required by the job
     * @param maxBacklogsAllowed maximum backlogs the job tolerates
     * @param departments        eligible departments, or empty/["ALL"] for no restriction
     */
    public List<Student> findCandidatesByCriteria(double minCgpa, int maxBacklogsAllowed, String[] departments) {
        boolean restrictDept = departments.length > 0 && !departments[0].equals("ALL");

        StringBuilder sql = new StringBuilder("SELECT * FROM students WHERE cgpa >= ? AND backlogs <= ?");
        if (restrictDept) {
            sql.append(" AND department IN (")
                    .append(String.join(",", java.util.Collections.nCopies(departments.length, "?")))
                    .append(")");
        }

        List<Student> results = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setDouble(idx++, minCgpa);
            ps.setInt(idx++, maxBacklogsAllowed);
            if (restrictDept) {
                for (String dept : departments) {
                    ps.setString(idx++, dept);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch candidate students: " + e.getMessage(), e);
        }
        return results;
    }

    private long countFor(String sql, int id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count related records: " + e.getMessage(), e);
        }
    }

    /** Maps the current row of a ResultSet to a Student object. */
    private Student mapRow(ResultSet rs) throws SQLException {
        Student s = new Student();
        s.setStudentId(rs.getInt("student_id"));
        s.setRegisterNumber(rs.getString("register_number"));
        s.setName(rs.getString("name"));
        s.setDepartment(rs.getString("department"));
        s.setCgpa(rs.getDouble("cgpa"));
        s.setBacklogs(rs.getInt("backlogs"));
        s.setSkills(rs.getString("skills"));
        s.setEmail(rs.getString("email"));
        s.setPhone(rs.getString("phone"));
        return s;
    }
}
