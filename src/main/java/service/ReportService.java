package service;

import dao.ReportDAO;

import java.util.Map;

/**
 * Generates placement reports and statistics.
 * <p>
 * All the actual counting/grouping/max-finding is done in SQL by
 * {@link ReportDAO}, so this class stays a thin layer: it asks the database
 * for numbers that are already computed rather than pulling every row into
 * Java and aggregating them with loops.
 * <p>
 * Web version: printSummary (console output) is replaced by {@link #getSummary()},
 * which returns the same figures as one object for the dashboard page.
 */
public class ReportService {

    /** Everything the placement summary report shows. */
    public record PlacementSummary(long totalStudents, long totalCompanies, long totalJobs,
                                   long totalApplications, long totalSelected, long totalOffers,
                                   double highestPackage,
                                   Map<String, Long> selectedByDepartment,
                                   Map<String, Long> selectedByCompany) {
    }

    private final ReportDAO reportDAO;

    public ReportService(ReportDAO reportDAO) {
        this.reportDAO = reportDAO;
    }

    public long totalSelected() {
        return reportDAO.totalSelected();
    }

    public long totalOffers() {
        return reportDAO.totalOffers();
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

    /**
     * The full placement summary report (what the console's printSummary printed).
     * The six counts come from one query; the package and the two breakdowns from three more.
     */
    public PlacementSummary getSummary() {
        ReportDAO.Totals t = reportDAO.totals();
        return new PlacementSummary(
                t.students(),
                t.companies(),
                t.jobs(),
                t.applications(),
                t.selected(),
                t.offers(),
                highestPackageOffered(),
                selectedCountByDepartment(),
                selectedCountByCompany());
    }
}
