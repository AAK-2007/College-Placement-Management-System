import dao.*;
import db.DBConnection;
import model.*;
import service.EligibilityService;
import service.PlacementService;
import service.ReportService;
import util.InputValidator;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

/**
 * Entry point and console-based user interface for the College Placement
 * Management & Eligibility Analysis System.
 *
 * Main is intentionally thin: it only reads menu choices and calls into
 * the DAO/service layer. All business rules live in the `service` package
 * and all persistence lives in the `dao` package, so this class can be
 * swapped for a GUI or web front-end later without touching the logic.
 */
public class Main {

    private static final Scanner scanner = new Scanner(System.in);
    private static final InputValidator input = new InputValidator(scanner);

    private static final StudentDAO studentDAO = new StudentDAO();
    private static final CompanyDAO companyDAO = new CompanyDAO();
    private static final JobDAO jobDAO = new JobDAO();
    private static final ApplicationDAO applicationDAO = new ApplicationDAO();
    private static final InterviewDAO interviewDAO = new InterviewDAO();
    private static final ReportDAO reportDAO = new ReportDAO();

    private static final EligibilityService eligibilityService = new EligibilityService(studentDAO, jobDAO);
    private static final PlacementService placementService =
            new PlacementService(studentDAO, jobDAO, applicationDAO, interviewDAO, eligibilityService);
    private static final ReportService reportService = new ReportService(studentDAO, jobDAO, reportDAO);

    public static void main(String[] args) {
        try {
            DBConnection.getConnection(); // fail fast if DB is unreachable
            boolean running = true;
            while (running) {
                printMainMenu();
                int choice = input.readInt("Enter choice: ");
                switch (choice) {
                    case 1 -> studentMenu();
                    case 2 -> companyMenu();
                    case 3 -> jobMenu();
                    case 4 -> eligibilityMenu();
                    case 5 -> applicationMenu();
                    case 6 -> interviewMenu();
                    case 7 -> reportService.printSummary();
                    case 0 -> running = false;
                    default -> System.out.println("Invalid choice, try again.");
                }
            }
        } finally {
            DBConnection.close();
            scanner.close();
        }
        System.out.println("Goodbye!");
    }

    private static void printMainMenu() {
        System.out.println("""

                ===== COLLEGE PLACEMENT MANAGEMENT & ELIGIBILITY ANALYSIS SYSTEM =====
                1. Student Management
                2. Company Management
                3. Job Management
                4. Eligibility Analysis
                5. Applications
                6. Interviews & Selection
                7. Placement Reports
                0. Exit
                """);
    }

    // ---------------------------------------------------------------
    // Student management
    // ---------------------------------------------------------------
    private static void studentMenu() {
        System.out.println("""
                -- Student Management --
                1. Add Student
                2. View All Students
                3. Update Student
                4. Delete Student
                0. Back
                """);
        int choice = input.readInt("Enter choice: ");
        switch (choice) {
            case 1 -> addStudent();
            case 2 -> studentDAO.getAllStudents().forEach(System.out::println);
            case 3 -> updateStudent();
            case 4 -> {
                int id = input.readInt("Student ID to delete: ");
                System.out.println(studentDAO.deleteStudent(id) ? "Deleted." : "Student not found.");
            }
            case 0 -> { /* back to main menu */ }
            default -> System.out.println("Invalid choice.");
        }
    }

    private static void addStudent() {
        String reg = input.readNonEmptyString("Register Number: ");
        String name = input.readNonEmptyString("Name: ");
        String dept = input.readNonEmptyString("Department (e.g. CSE): ");
        double cgpa = input.readDouble("CGPA: ");
        int backlogs = input.readInt("Backlogs: ");
        String skills = input.readNonEmptyString("Skills (comma-separated): ");
        String email = input.readNonEmptyString("Email: ");
        String phone = input.readNonEmptyString("Phone: ");

        Student student = new Student(reg, name, dept, cgpa, backlogs, skills, email, phone);
        int id = studentDAO.addStudent(student);
        System.out.println(id > 0 ? "Student added with ID " + id : "Failed to add student.");
    }

    private static void updateStudent() {
        int id = input.readInt("Student ID to update: ");
        Student existing = studentDAO.getStudentById(id);
        if (existing == null) {
            System.out.println("Student not found.");
            return;
        }
        existing.setName(input.readNonEmptyString("New Name (" + existing.getName() + "): "));
        existing.setDepartment(input.readNonEmptyString("New Department (" + existing.getDepartment() + "): "));
        existing.setCgpa(input.readDouble("New CGPA (" + existing.getCgpa() + "): "));
        existing.setBacklogs(input.readInt("New Backlogs (" + existing.getBacklogs() + "): "));
        existing.setSkills(input.readNonEmptyString("New Skills (" + existing.getSkills() + "): "));
        existing.setEmail(input.readNonEmptyString("New Email (" + existing.getEmail() + "): "));
        existing.setPhone(input.readNonEmptyString("New Phone (" + existing.getPhone() + "): "));
        System.out.println(studentDAO.updateStudent(existing) ? "Updated." : "Update failed.");
    }

    // ---------------------------------------------------------------
    // Company management
    // ---------------------------------------------------------------
    private static void companyMenu() {
        System.out.println("""
                -- Company Management --
                1. Add Company
                2. View All Companies
                3. Delete Company
                0. Back
                """);
        int choice = input.readInt("Enter choice: ");
        switch (choice) {
            case 1 -> {
                String name = input.readNonEmptyString("Company Name: ");
                String sector = input.readNonEmptyString("Sector: ");
                String email = input.readNonEmptyString("Contact Email: ");
                String phone = input.readNonEmptyString("Contact Phone: ");
                int id = companyDAO.addCompany(new Company(name, sector, email, phone));
                System.out.println(id > 0 ? "Company added with ID " + id : "Failed to add company.");
            }
            case 2 -> companyDAO.getAllCompanies().forEach(System.out::println);
            case 3 -> {
                int id = input.readInt("Company ID to delete: ");
                System.out.println(companyDAO.deleteCompany(id) ? "Deleted." : "Company not found.");
            }
            case 0 -> { /* back */ }
            default -> System.out.println("Invalid choice.");
        }
    }

    // ---------------------------------------------------------------
    // Job management
    // ---------------------------------------------------------------
    private static void jobMenu() {
        System.out.println("""
                -- Job Management --
                1. Add Job Opening
                2. View All Jobs
                3. View Jobs by Company
                0. Back
                """);
        int choice = input.readInt("Enter choice: ");
        switch (choice) {
            case 1 -> addJob();
            case 2 -> jobDAO.getAllJobs().forEach(System.out::println);
            case 3 -> {
                int companyId = input.readInt("Company ID: ");
                jobDAO.getJobsByCompany(companyId).forEach(System.out::println);
            }
            case 0 -> { /* back */ }
            default -> System.out.println("Invalid choice.");
        }
    }

    private static void addJob() {
        int companyId = input.readInt("Company ID: ");
        if (companyDAO.getCompanyById(companyId) == null) {
            System.out.println("No such company. Add the company first.");
            return;
        }
        String role = input.readNonEmptyString("Role/Title: ");
        double minCgpa = input.readDouble("Minimum CGPA required: ");
        String depts = input.readNonEmptyString("Eligible Departments (comma-separated, or ALL): ");
        int maxBacklogs = input.readInt("Maximum backlogs allowed: ");
        String skills = input.readNonEmptyString("Required Skills (comma-separated, or leave blank): ");
        double packageLpa = input.readDouble("Package offered (LPA): ");

        Job job = new Job(companyId, role, minCgpa, depts, maxBacklogs, skills, packageLpa);
        int id = jobDAO.addJob(job);
        System.out.println(id > 0 ? "Job added with ID " + id : "Failed to add job.");
    }

    // ---------------------------------------------------------------
    // Eligibility analysis
    // ---------------------------------------------------------------
    private static void eligibilityMenu() {
        System.out.println("""
                -- Eligibility Analysis --
                1. Find eligible students for a job
                2. Find eligible jobs for a student
                3. Explain eligibility (student vs job)
                0. Back
                """);
        int choice = input.readInt("Enter choice: ");
        switch (choice) {
            case 1 -> {
                Job job = jobDAO.getJobById(input.readInt("Job ID: "));
                if (job == null) {
                    System.out.println("Job not found.");
                    return;
                }
                List<Student> eligible = eligibilityService.getEligibleStudentsForJob(job);
                System.out.println(eligible.isEmpty() ? "No eligible students." : "Eligible students:");
                eligible.forEach(System.out::println);
            }
            case 2 -> {
                Student student = studentDAO.getStudentById(input.readInt("Student ID: "));
                if (student == null) {
                    System.out.println("Student not found.");
                    return;
                }
                List<Job> eligible = eligibilityService.getEligibleJobsForStudent(student);
                System.out.println(eligible.isEmpty() ? "No eligible jobs." : "Eligible jobs:");
                eligible.forEach(System.out::println);
            }
            case 3 -> {
                Student student = studentDAO.getStudentById(input.readInt("Student ID: "));
                Job job = jobDAO.getJobById(input.readInt("Job ID: "));
                if (student == null || job == null) {
                    System.out.println("Student or job not found.");
                    return;
                }
                System.out.println(eligibilityService.explainEligibility(student, job));
            }
            case 0 -> { /* back */ }
            default -> System.out.println("Invalid choice.");
        }
    }

    // ---------------------------------------------------------------
    // Applications
    // ---------------------------------------------------------------
    private static void applicationMenu() {
        System.out.println("""
                -- Applications --
                1. Apply for a job
                2. View applications by student
                3. View applications for a job
                4. Shortlist an application
                0. Back
                """);
        int choice = input.readInt("Enter choice: ");
        switch (choice) {
            case 1 -> {
                int studentId = input.readInt("Student ID: ");
                int jobId = input.readInt("Job ID: ");
                PlacementService.ApplyResult result = placementService.apply(studentId, jobId);
                System.out.println(switch (result) {
                    case SUCCESS -> "Application submitted successfully.";
                    case ALREADY_APPLIED -> "You have already applied to this job.";
                    case NOT_ELIGIBLE -> "Not eligible for this job. Use Eligibility Analysis > Explain for details.";
                    case STUDENT_NOT_FOUND -> "Student not found.";
                    case JOB_NOT_FOUND -> "Job not found.";
                });
            }
            case 2 -> applicationDAO.getApplicationsByStudent(input.readInt("Student ID: "))
                    .forEach(System.out::println);
            case 3 -> applicationDAO.getApplicationsByJob(input.readInt("Job ID: "))
                    .forEach(System.out::println);
            case 4 -> {
                int appId = input.readInt("Application ID: ");
                System.out.println(placementService.shortlist(appId) ? "Shortlisted." : "Application not found.");
            }
            case 0 -> { /* back */ }
            default -> System.out.println("Invalid choice.");
        }
    }

    // ---------------------------------------------------------------
    // Interviews & selection
    // ---------------------------------------------------------------
    private static void interviewMenu() {
        System.out.println("""
                -- Interviews & Selection --
                1. Schedule interview round
                2. Record interview result
                3. View interview rounds for an application
                4. Finalize selection decision
                0. Back
                """);
        int choice = input.readInt("Enter choice: ");
        switch (choice) {
            case 1 -> {
                int appId = input.readInt("Application ID: ");
                String round = input.readNonEmptyString("Round name (e.g. Technical): ");
                int interviewId = placementService.scheduleInterview(appId, LocalDate.now(), round);
                System.out.println("Interview scheduled with ID " + interviewId);
            }
            case 2 -> {
                int interviewId = input.readInt("Interview ID: ");
                String resultStr = input.readNonEmptyString("Result (PASS/FAIL): ").toUpperCase();
                String remarks = input.readNonEmptyString("Remarks: ");
                try {
                    InterviewResult result = InterviewResult.valueOf(resultStr);
                    System.out.println(placementService.recordInterviewResult(interviewId, result, remarks)
                            ? "Result recorded." : "Interview not found.");
                } catch (IllegalArgumentException e) {
                    System.out.println("Result must be PASS or FAIL.");
                }
            }
            case 3 -> interviewDAO.getInterviewsByApplication(input.readInt("Application ID: "))
                    .forEach(System.out::println);
            case 4 -> {
                int appId = input.readInt("Application ID: ");
                ApplicationStatus outcome = placementService.finalizeSelection(appId);
                System.out.println("Current status: " + outcome);
            }
            case 0 -> { /* back */ }
            default -> System.out.println("Invalid choice.");
        }
    }
}
