package web;

import dao.*;
import dao.ApplicationDAO.ApplicationView;
import db.DBConnection;
import model.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import service.EligibilityService;
import service.PlacementService;
import service.ReportService;
import web.config.SecurityConfig;
import web.security.AppUser;
import web.security.Role;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who can open what. Logs in as each role (no database: DAOs and services are mocks) and checks
 * both the page rules and that students / companies only reach their own records.
 */
@WebMvcTest
@Import(SecurityConfig.class)
class AccessControlTest {

    private static final int MY_STUDENT = 1;
    private static final int OTHER_STUDENT = 2;
    private static final int MY_COMPANY = 7;
    private static final int OTHER_COMPANY = 8;

    @Autowired
    private MockMvc mvc;

    @MockitoBean private DBConnection dbConnection;
    @MockitoBean private StudentDAO studentDAO;
    @MockitoBean private CompanyDAO companyDAO;
    @MockitoBean private JobDAO jobDAO;
    @MockitoBean private ApplicationDAO applicationDAO;
    @MockitoBean private InterviewDAO interviewDAO;
    @MockitoBean private ReportDAO reportDAO;
    @MockitoBean private UserDAO userDAO;
    @MockitoBean private EligibilityService eligibilityService;
    @MockitoBean private PlacementService placementService;
    @MockitoBean private ReportService reportService;

    private static RequestPostProcessor officer() {
        return user(new AppUser("officer", "x", Role.OFFICER, null, null, "Placement Officer"));
    }

    private static RequestPostProcessor student() {
        return user(new AppUser("stud", "x", Role.STUDENT, MY_STUDENT, null, "Asha"));
    }

    private static RequestPostProcessor company() {
        return user(new AppUser("comp", "x", Role.COMPANY, null, MY_COMPANY, "Nimbus"));
    }

    /** An application (id 5) to a job of the given company. */
    private void givenApplicationOfCompany(int companyId) {
        when(applicationDAO.getApplicationView(5)).thenReturn(new ApplicationView(
                5, OTHER_STUDENT, "Ravi", "R2", 10, companyId, "Co", "Dev", LocalDate.now(), ApplicationStatus.APPLIED));
    }

    @Nested
    class Visitor {

        @Test
        void seesTheWelcomeAndSignUpPages() throws Exception {
            mvc.perform(get("/")).andExpect(status().isOk());
            mvc.perform(get("/login")).andExpect(status().isOk());
            mvc.perform(get("/register/student")).andExpect(status().isOk());
            mvc.perform(get("/register/company")).andExpect(status().isOk());
        }

        @Test
        void isSentToLoginForEverythingElse() throws Exception {
            for (String url : new String[]{"/students", "/companies", "/jobs", "/applications", "/interviews", "/eligibility"}) {
                mvc.perform(get(url)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
            }
        }
    }

    @Nested
    class Student {

        @Test
        void cannotOpenOfficerOrCompanyPages() throws Exception {
            for (String url : new String[]{"/students", "/students/2/edit", "/students/add", "/companies/add",
                    "/jobs/add", "/eligibility/job", "/interviews", "/interviews/application/5"}) {
                mvc.perform(get(url).with(student())).andExpect(status().isForbidden());
            }
            mvc.perform(post("/companies/1/delete").with(student()).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post("/applications/5/shortlist").with(student()).with(csrf())).andExpect(status().isForbidden());
            verifyNoInteractions(placementService);
        }

        @Test
        void canViewCompaniesJobsAndOwnProfile() throws Exception {
            when(studentDAO.getStudentById(MY_STUDENT)).thenReturn(new model.Student("R1", "Asha", "CSE", 8, 0, "Java", null, null));
            mvc.perform(get("/companies").with(student())).andExpect(status().isOk());
            mvc.perform(get("/jobs").with(student())).andExpect(status().isOk());
            mvc.perform(get("/students/me").with(student())).andExpect(status().isOk());
        }

        @Test
        void appliesOnlyForThemselves() throws Exception {
            when(placementService.apply(anyInt(), anyInt())).thenReturn(PlacementService.ApplyResult.SUCCESS);

            mvc.perform(post("/applications/apply").with(student()).with(csrf())
                            .param("studentId", String.valueOf(OTHER_STUDENT)).param("jobId", "10"))
                    .andExpect(status().is3xxRedirection());

            verify(placementService).apply(MY_STUDENT, 10);
            verify(placementService, never()).apply(eq(OTHER_STUDENT), anyInt());
        }

        @Test
        void seesOnlyOwnApplications() throws Exception {
            mvc.perform(get("/applications").with(student()).param("studentId", String.valueOf(OTHER_STUDENT)))
                    .andExpect(status().isOk());
            verify(applicationDAO).getApplicationViews(MY_STUDENT, null, null);
        }

        @Test
        void eligibilityIsAlwaysTheirOwn() throws Exception {
            mvc.perform(get("/eligibility/explain").with(student())
                    .param("studentId", String.valueOf(OTHER_STUDENT)).param("jobId", "10")).andExpect(status().isOk());
            mvc.perform(get("/eligibility/student").with(student())
                    .param("studentId", String.valueOf(OTHER_STUDENT))).andExpect(status().isOk());
            verify(studentDAO, never()).getStudentById(OTHER_STUDENT);
            verify(studentDAO, atLeastOnce()).getStudentById(MY_STUDENT);
        }
    }

    @Nested
    class Company {

        @Test
        void cannotOpenStudentOrOfficerPages() throws Exception {
            for (String url : new String[]{"/students", "/students/me", "/companies", "/companies/add",
                    "/eligibility/student", "/eligibility/explain", "/applications/apply"}) {
                mvc.perform(get(url).with(company())).andExpect(status().isForbidden());
            }
        }

        @Test
        void seesOnlyOwnJobs() throws Exception {
            mvc.perform(get("/jobs").with(company()).param("companyId", String.valueOf(OTHER_COMPANY)))
                    .andExpect(status().isOk());
            verify(jobDAO).getJobsByCompany(MY_COMPANY);
            verify(jobDAO, never()).getJobsByCompany(OTHER_COMPANY);
            verify(jobDAO, never()).getAllJobs();
        }

        @Test
        void postsJobsOnlyForItself() throws Exception {
            model.Company own = new model.Company("Nimbus", null, null, null);
            own.setCompanyId(MY_COMPANY);
            when(companyDAO.getCompanyById(MY_COMPANY)).thenReturn(own);

            mvc.perform(post("/jobs/add").with(company()).with(csrf())
                            .param("companyId", String.valueOf(OTHER_COMPANY)).param("role", "Dev")
                            .param("minCgpa", "7").param("eligibleDepartments", "CSE")
                            .param("maxBacklogs", "0").param("packageLpa", "6"))
                    .andExpect(status().is3xxRedirection());

            ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
            verify(jobDAO).addJob(saved.capture());
            assertEquals(MY_COMPANY, saved.getValue().getCompanyId());
        }

        @Test
        void seesOnlyApplicationsToOwnJobs() throws Exception {
            mvc.perform(get("/applications").with(company())).andExpect(status().isOk());
            verify(applicationDAO).getApplicationViews(null, null, MY_COMPANY);
            mvc.perform(get("/interviews").with(company())).andExpect(status().isOk());
            verify(applicationDAO, times(2)).getApplicationViews(null, null, MY_COMPANY);
        }

        @Test
        void cannotTouchAnotherCompanysApplication() throws Exception {
            givenApplicationOfCompany(OTHER_COMPANY);
            Interview round = new Interview(5, LocalDate.now(), "HR", InterviewResult.PENDING, "");
            round.setInterviewId(9);
            when(interviewDAO.getInterviewById(9)).thenReturn(round);

            mvc.perform(post("/applications/5/shortlist").with(company()).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(get("/interviews/application/5").with(company())).andExpect(status().isForbidden());
            mvc.perform(post("/interviews/application/5/schedule").with(company()).with(csrf())
                    .param("round", "HR")).andExpect(status().isForbidden());
            mvc.perform(post("/interviews/9/result").with(company()).with(csrf())
                    .param("result", "PASS")).andExpect(status().isForbidden());
            mvc.perform(post("/interviews/application/5/finalize").with(company()).with(csrf())).andExpect(status().isForbidden());

            verifyNoInteractions(placementService);
        }

        @Test
        void canShortlistOwnApplication() throws Exception {
            givenApplicationOfCompany(MY_COMPANY);
            when(placementService.shortlist(5)).thenReturn(PlacementService.StepResult.OK);

            mvc.perform(post("/applications/5/shortlist").with(company()).with(csrf()))
                    .andExpect(status().is3xxRedirection());
            verify(placementService).shortlist(5);
        }
    }

    @Nested
    class Officer {

        @Test
        void canOpenEverySection() throws Exception {
            for (String url : new String[]{"/", "/students", "/students/add", "/companies", "/companies/add", "/jobs",
                    "/jobs/add", "/eligibility/job", "/eligibility/student", "/eligibility/explain",
                    "/applications", "/applications/apply", "/interviews"}) {
                mvc.perform(get(url).with(officer())).andExpect(status().isOk());
            }
        }

        @Test
        void canWorkOnAnyCompanysApplication() throws Exception {
            givenApplicationOfCompany(OTHER_COMPANY);
            when(placementService.shortlist(5)).thenReturn(PlacementService.StepResult.OK);
            mvc.perform(post("/applications/5/shortlist").with(officer()).with(csrf()))
                    .andExpect(status().is3xxRedirection());
            verify(placementService).shortlist(5);
        }
    }
}
