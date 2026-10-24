package web.config;

import dao.ApplicationDAO;
import dao.CompanyDAO;
import dao.InterviewDAO;
import dao.JobDAO;
import dao.ReportDAO;
import dao.StudentDAO;
import dao.UserDAO;
import db.DBConnection;
import org.springframework.context.annotation.Bean;
import service.EligibilityService;
import service.PlacementService;
import service.ReportService;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Creates the DAO and service objects. They stay plain Java classes (no Spring
 * annotations), exactly like the console version, so they can be unit-tested
 * without starting Spring or a database.
 */
@Configuration
public class AppConfig {

    @Bean
    public DBConnection dbConnection(DataSource dataSource) {
        return new DBConnection(dataSource);
    }

    // ---- DAOs ----

    @Bean
    public StudentDAO studentDAO(DBConnection db) {
        return new StudentDAO(db);
    }

    @Bean
    public CompanyDAO companyDAO(DBConnection db) {
        return new CompanyDAO(db);
    }

    @Bean
    public JobDAO jobDAO(DBConnection db) {
        return new JobDAO(db);
    }

    @Bean
    public ApplicationDAO applicationDAO(DBConnection db) {
        return new ApplicationDAO(db);
    }

    @Bean
    public InterviewDAO interviewDAO(DBConnection db) {
        return new InterviewDAO(db);
    }

    @Bean
    public ReportDAO reportDAO(DBConnection db) {
        return new ReportDAO(db);
    }

    @Bean
    public UserDAO userDAO(DBConnection db) {
        return new UserDAO(db);
    }

    // ---- Services ----

    @Bean
    public ReportService reportService(ReportDAO reportDAO) {
        return new ReportService(reportDAO);
    }

    @Bean
    public PlacementService placementService(StudentDAO studentDAO, JobDAO jobDAO, ApplicationDAO applicationDAO,
                                             InterviewDAO interviewDAO, EligibilityService eligibilityService) {
        return new PlacementService(studentDAO, jobDAO, applicationDAO, interviewDAO, eligibilityService);
    }

    @Bean
    public EligibilityService eligibilityService(StudentDAO studentDAO, JobDAO jobDAO) {
        return new EligibilityService(studentDAO, jobDAO);
    }
}
