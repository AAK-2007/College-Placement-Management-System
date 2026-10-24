-- Login accounts for students and companies (the placement officer account is configured in
-- application.properties). Run automatically at startup; safe to run again.
-- Each account belongs to exactly one student or one company; deleting that record deletes the account.
CREATE TABLE IF NOT EXISTS users (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    student_id    INT NULL UNIQUE,
    company_id    INT NULL UNIQUE,
    FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE,
    FOREIGN KEY (company_id) REFERENCES companies(company_id) ON DELETE CASCADE
);
