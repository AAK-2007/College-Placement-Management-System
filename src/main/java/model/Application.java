package model;

import java.time.LocalDate;

/** Represents a student's application to a specific job opening. */
public class Application {

    private int applicationId;
    private int studentId;
    private int jobId;
    private LocalDate applicationDate;
    private ApplicationStatus status;

    public Application() {
    }

    public Application(int studentId, int jobId, LocalDate applicationDate, ApplicationStatus status) {
        this.studentId = studentId;
        this.jobId = jobId;
        this.applicationDate = applicationDate;
        this.status = status;
    }

    public int getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(int applicationId) {
        this.applicationId = applicationId;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public LocalDate getApplicationDate() {
        return applicationDate;
    }

    public void setApplicationDate(LocalDate applicationDate) {
        this.applicationDate = applicationDate;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return String.format("[App#%d] Student:%d -> Job:%d | Date: %s | Status: %s",
                applicationId, studentId, jobId, applicationDate, status);
    }
}
