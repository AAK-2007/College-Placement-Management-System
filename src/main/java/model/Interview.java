package model;

import java.time.LocalDate;

/** Represents an interview round conducted for a given application. */
public class Interview {

    private int interviewId;
    private int applicationId;
    private LocalDate interviewDate;
    private String round;           // e.g. "Technical", "HR", "Aptitude"
    private InterviewResult result;
    private String remarks;

    public Interview() {
    }

    public Interview(int applicationId, LocalDate interviewDate, String round,
                      InterviewResult result, String remarks) {
        this.applicationId = applicationId;
        this.interviewDate = interviewDate;
        this.round = round;
        this.result = result;
        this.remarks = remarks;
    }

    public int getInterviewId() {
        return interviewId;
    }

    public void setInterviewId(int interviewId) {
        this.interviewId = interviewId;
    }

    public int getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(int applicationId) {
        this.applicationId = applicationId;
    }

    public LocalDate getInterviewDate() {
        return interviewDate;
    }

    public void setInterviewDate(LocalDate interviewDate) {
        this.interviewDate = interviewDate;
    }

    public String getRound() {
        return round;
    }

    public void setRound(String round) {
        this.round = round;
    }

    public InterviewResult getResult() {
        return result;
    }

    public void setResult(InterviewResult result) {
        this.result = result;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    @Override
    public String toString() {
        return String.format("[Interview#%d] App:%d | Round: %s | Date: %s | Result: %s | Remarks: %s",
                interviewId, applicationId, round, interviewDate, result, remarks);
    }
}
