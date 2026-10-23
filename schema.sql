-- College Placement Management & Eligibility Analysis System
-- MySQL schema. Run this once before starting the Java application.

CREATE DATABASE IF NOT EXISTS placement_system;
USE placement_system;

CREATE TABLE IF NOT EXISTS students (
    student_id      INT AUTO_INCREMENT PRIMARY KEY,
    register_number VARCHAR(20)  NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    department      VARCHAR(50)  NOT NULL,
    cgpa            DECIMAL(4,2) NOT NULL,
    backlogs        INT          NOT NULL DEFAULT 0,
    skills          VARCHAR(255),
    email           VARCHAR(100),
    phone           VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS companies (
    company_id    INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    sector        VARCHAR(100),
    contact_email VARCHAR(100),
    contact_phone VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS jobs (
    job_id               INT AUTO_INCREMENT PRIMARY KEY,
    company_id           INT NOT NULL,
    role                 VARCHAR(100) NOT NULL,
    min_cgpa             DECIMAL(4,2) NOT NULL DEFAULT 0,
    eligible_departments VARCHAR(255) NOT NULL DEFAULT 'ALL',
    max_backlogs         INT NOT NULL DEFAULT 0,
    required_skills      VARCHAR(255),
    package_lpa          DECIMAL(6,2) NOT NULL DEFAULT 0,
    FOREIGN KEY (company_id) REFERENCES companies(company_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS applications (
    application_id   INT AUTO_INCREMENT PRIMARY KEY,
    student_id       INT NOT NULL,
    job_id           INT NOT NULL,
    application_date DATE NOT NULL,
    status           VARCHAR(30) NOT NULL DEFAULT 'APPLIED',
    FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE,
    FOREIGN KEY (job_id) REFERENCES jobs(job_id) ON DELETE CASCADE,
    UNIQUE KEY uniq_student_job (student_id, job_id)
);

CREATE TABLE IF NOT EXISTS interviews (
    interview_id    INT AUTO_INCREMENT PRIMARY KEY,
    application_id  INT NOT NULL,
    interview_date  DATE NOT NULL,
    round           VARCHAR(50) NOT NULL,
    result          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    remarks         VARCHAR(255),
    FOREIGN KEY (application_id) REFERENCES applications(application_id) ON DELETE CASCADE
);

-- Helpful indexes for the most common lookups
CREATE INDEX idx_jobs_company ON jobs(company_id);
CREATE INDEX idx_applications_student ON applications(student_id);
CREATE INDEX idx_applications_job ON applications(job_id);
CREATE INDEX idx_interviews_application ON interviews(application_id);
