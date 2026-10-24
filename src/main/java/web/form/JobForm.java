package web.form;

/** What the officer typed into the add job form (raw text). */
public class JobForm {

    private String companyId;
    private String role;
    private String minCgpa;
    private String eligibleDepartments = "ALL";
    private String maxBacklogs = "0";
    private String requiredSkills;
    private String packageLpa;

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMinCgpa() {
        return minCgpa;
    }

    public void setMinCgpa(String minCgpa) {
        this.minCgpa = minCgpa;
    }

    public String getEligibleDepartments() {
        return eligibleDepartments;
    }

    public void setEligibleDepartments(String eligibleDepartments) {
        this.eligibleDepartments = eligibleDepartments;
    }

    public String getMaxBacklogs() {
        return maxBacklogs;
    }

    public void setMaxBacklogs(String maxBacklogs) {
        this.maxBacklogs = maxBacklogs;
    }

    public String getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(String requiredSkills) {
        this.requiredSkills = requiredSkills;
    }

    public String getPackageLpa() {
        return packageLpa;
    }

    public void setPackageLpa(String packageLpa) {
        this.packageLpa = packageLpa;
    }
}
