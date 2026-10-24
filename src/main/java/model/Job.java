package model;

/**
 * Represents a job opening posted by a company, together with the
 * eligibility criteria that a student must satisfy to apply.
 */
public class Job {

    private int jobId;
    private int companyId;
    private String role;
    private double minCgpa;
    private String eligibleDepartments; // comma-separated, e.g. "CSE,IT" or "ALL"
    private int maxBacklogs;
    private String requiredSkills;      // comma-separated, e.g. "Java,SQL"
    private double packageLpa;          // annual package offered, in LPA

    public Job() {
    }

    public Job(int companyId, String role, double minCgpa, String eligibleDepartments,
               int maxBacklogs, String requiredSkills, double packageLpa) {
        this.companyId = companyId;
        this.role = role;
        this.minCgpa = minCgpa;
        this.eligibleDepartments = eligibleDepartments;
        this.maxBacklogs = maxBacklogs;
        this.requiredSkills = requiredSkills;
        this.packageLpa = packageLpa;
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public int getCompanyId() {
        return companyId;
    }

    public void setCompanyId(int companyId) {
        this.companyId = companyId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public double getMinCgpa() {
        return minCgpa;
    }

    public void setMinCgpa(double minCgpa) {
        this.minCgpa = minCgpa;
    }

    public String getEligibleDepartments() {
        return eligibleDepartments;
    }

    public void setEligibleDepartments(String eligibleDepartments) {
        this.eligibleDepartments = eligibleDepartments;
    }

    public int getMaxBacklogs() {
        return maxBacklogs;
    }

    public void setMaxBacklogs(int maxBacklogs) {
        this.maxBacklogs = maxBacklogs;
    }

    public String getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(String requiredSkills) {
        this.requiredSkills = requiredSkills;
    }

    public double getPackageLpa() {
        return packageLpa;
    }

    public void setPackageLpa(double packageLpa) {
        this.packageLpa = packageLpa;
    }

    /** Splits the required-skills string into individual lowercase tokens. */
    public String[] getRequiredSkillList() {
        if (requiredSkills == null || requiredSkills.isBlank()) {
            return new String[0];
        }
        String[] parts = requiredSkills.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim().toLowerCase();
        }
        return parts;
    }

    /** Splits the eligible-departments string into individual uppercase tokens. */
    public String[] getEligibleDepartmentList() {
        if (eligibleDepartments == null || eligibleDepartments.isBlank()) {
            return new String[0];
        }
        String[] parts = eligibleDepartments.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim().toUpperCase();
        }
        return parts;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s | Min CGPA: %.2f | Dept: %s | Max Backlogs: %d | Skills: %s | Package: %.2f LPA",
                jobId, role, minCgpa, eligibleDepartments, maxBacklogs, requiredSkills, packageLpa);
    }
}
