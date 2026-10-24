-- Made-up sample data for trying out the placement web app.
-- Run AFTER schema.sql, on an empty placement_system database.
-- (On a database that already has these register numbers, the student inserts fail.)
--
-- Contains students, companies and jobs only - no applications - so you can try the
-- whole flow yourself: Eligibility -> Apply -> Shortlist -> Interviews -> Finalize.

USE placement_system;

-- ---------------- Students ----------------
INSERT INTO students (register_number, name, department, cgpa, backlogs, skills, email, phone) VALUES
('21CS001', 'Ananya Rao',     'CSE',  9.10, 0, 'Java, SQL, Spring, DSA',  'ananya.rao@example.com',   '9000000001'),
('21CS002', 'Rahul Menon',    'CSE',  7.40, 1, 'Python, SQL',              'rahul.menon@example.com',  '9000000002'),
('21CS003', 'Fatima Sheikh',  'CSE',  6.80, 0, 'C, C++',                   'fatima.s@example.com',     NULL),
('21IT001', 'Vikram Iyer',    'IT',   8.20, 0, 'Java, JavaScript, React',  'vikram.iyer@example.com',  '9000000004'),
('21IT002', 'Sneha Pillai',   'IT',   7.90, 2, 'Python, Django, SQL',      NULL,                       '9000000005'),
('21EC001', 'Karthik Nair',   'ECE',  8.60, 0, 'C, Embedded C, MATLAB',    'karthik.nair@example.com', '9000000006'),
('21EC002', 'Divya Krishnan', 'ECE',  7.10, 1, 'Python, MATLAB',           'divya.k@example.com',      NULL),
('21ME001', 'Arjun Das',      'MECH', 7.80, 0, 'AutoCAD, SolidWorks, Python', 'arjun.das@example.com', '9000000008');

-- ---------------- Companies ----------------
INSERT INTO companies (name, sector, contact_email, contact_phone) VALUES
('Nimbus Software',   'IT Services',     'hr@nimbus.example.com',    '0441000001'),
('Voltline Systems',  'Electronics',     'jobs@voltline.example.com', NULL),
('Kestrel Analytics', 'Data Analytics',  'careers@kestrel.example.com', '0441000003'),
('Ironclad Motors',   'Automotive',      NULL,                        '0441000004');

-- ---------------- Jobs ----------------
-- company_id is looked up by name so this file does not depend on auto-increment values.
INSERT INTO jobs (company_id, role, min_cgpa, eligible_departments, max_backlogs, required_skills, package_lpa)
SELECT company_id, 'Software Engineer', 7.50, 'CSE,IT', 0, 'Java, Spring, SQL', 8.50 FROM companies WHERE name = 'Nimbus Software';

INSERT INTO jobs (company_id, role, min_cgpa, eligible_departments, max_backlogs, required_skills, package_lpa)
SELECT company_id, 'Graduate Trainee', 6.50, 'ALL', 2, NULL, 4.00 FROM companies WHERE name = 'Nimbus Software';

INSERT INTO jobs (company_id, role, min_cgpa, eligible_departments, max_backlogs, required_skills, package_lpa)
SELECT company_id, 'Embedded Engineer', 7.00, 'ECE,CSE', 1, 'C, Embedded C', 6.00 FROM companies WHERE name = 'Voltline Systems';

INSERT INTO jobs (company_id, role, min_cgpa, eligible_departments, max_backlogs, required_skills, package_lpa)
SELECT company_id, 'Data Analyst', 7.00, 'ALL', 1, 'Python, SQL, MATLAB', 7.00 FROM companies WHERE name = 'Kestrel Analytics';

INSERT INTO jobs (company_id, role, min_cgpa, eligible_departments, max_backlogs, required_skills, package_lpa)
SELECT company_id, 'Frontend Developer', 8.00, 'CSE,IT', 0, 'JavaScript, React', 9.00 FROM companies WHERE name = 'Kestrel Analytics';

INSERT INTO jobs (company_id, role, min_cgpa, eligible_departments, max_backlogs, required_skills, package_lpa)
SELECT company_id, 'Design Engineer', 7.00, 'MECH', 0, 'AutoCAD, SolidWorks', 5.50 FROM companies WHERE name = 'Ironclad Motors';
