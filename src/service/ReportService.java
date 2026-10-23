package service;

import dao.ReportDAO;
import dao.StudentDAO;
import dao.JobDAO;

import java.util.Map;

/**
 * Generates placement reports and statistics.
 * <p>
 * All the actual counting/grouping/max-finding is done in SQL by
 * {@link ReportDAO}, so this class stays a thin presentation layer: it
 * asks the database for numbers that are already computed rather than
 * pulling every application/student row into Java and aggregating them
 * with loops. That keeps report generation fast and its memory footprint
 * constant regardless of how many placement records exist.
 */
public class ReportService {

    private final StudentDAO studentDAO;
    private final JobDAO jobDAO;
    private final ReportDAO reportDAO;

    public ReportService(StudentDAO studentDAO, JobDAO jobDAO, ReportDAO reportDAO) {
        this.studentDAO = studentDAO;
        this.jobDAO = jobDAO;
        this.reportDAO = reportDAO;
    }

    public long totalSelected() {
        return reportDAO.totalSelected();
    }

    public long totalApplications() {
        return reportDAO.totalApplications();
    }

    public Map<String, Long> selectedCountByDepartment() {
        return reportDAO.selectedCountByDepartment();
    }

    public Map<String, Long> selectedCountByCompany() {
        return reportDAO.selectedCountByCompany();
    }

    public double highestPackageOffered() {
        return reportDAO.highestPackageOffered();
    }

    /** Prints a full placement summary to standard output. */
    public void printSummary() {
        System.out.println("\n===== PLACEMENT SUMMARY REPORT =====");
        System.out.println("Total students registered  : " + studentDAO.getAllStudents().size());
        System.out.println("Total job openings          : " + jobDAO.getAllJobs().size());
        System.out.println("Total applications          : " + totalApplications());
        System.out.println("Total students selected     : " + totalSelected());
        System.out.printf("Highest package offered      : %.2f LPA%n", highestPackageOffered());

        System.out.println("\n-- Department-wise selections --");
        selectedCountByDepartment().forEach((dept, count) -> System.out.println("  " + dept + ": " + count));

        System.out.println("\n-- Company-wise selections --");
        selectedCountByCompany().forEach((company, count) -> System.out.println("  " + company + ": " + count));
        System.out.println("=====================================\n");
    }
}
