package model;

/**
 * Represents a student's academic and personal record.
 * A plain data-holding entity (POJO) - all validation happens before an
 * instance is built, in the service/DAO layer, keeping this class simple.
 */
public class Student {

    private int studentId;          // primary key, 0 until persisted
    private String registerNumber;  // college register number, unique
    private String name;
    private String department;
    private double cgpa;
    private int backlogs;
    private String skills;          // comma-separated skill tags e.g. "Java,SQL,DSA"
    private String email;
    private String phone;

    public Student() {
    }

    public Student(String registerNumber, String name, String department,
                   double cgpa, int backlogs, String skills, String email, String phone) {
        this.registerNumber = registerNumber;
        this.name = name;
        this.department = department;
        this.cgpa = cgpa;
        this.backlogs = backlogs;
        this.skills = skills;
        this.email = email;
        this.phone = phone;
    }

    // ---- Getters and setters ----

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public String getRegisterNumber() {
        return registerNumber;
    }

    public void setRegisterNumber(String registerNumber) {
        this.registerNumber = registerNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public double getCgpa() {
        return cgpa;
    }

    public void setCgpa(double cgpa) {
        this.cgpa = cgpa;
    }

    public int getBacklogs() {
        return backlogs;
    }

    public void setBacklogs(int backlogs) {
        this.backlogs = backlogs;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    /** Splits the stored comma-separated skills string into individual tokens. */
    public String[] getSkillList() {
        if (skills == null || skills.isBlank()) {
            return new String[0];
        }
        String[] parts = skills.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim().toLowerCase();
        }
        return parts;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s (%s) | Dept: %s | CGPA: %.2f | Backlogs: %d | Skills: %s",
                studentId, name, registerNumber, department, cgpa, backlogs, skills);
    }
}
