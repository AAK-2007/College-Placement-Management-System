# College Placement Management & Eligibility Analysis System

A Java + MySQL (JDBC) console application that manages the full campus
placement lifecycle: student records, company/job postings, automated
eligibility analysis, applications, interviews, final selection, and
placement reports/statistics.

This implements the project described in the abstract: Java performs all
processing and decision-making; MySQL is used purely for persistent
storage and retrieval.

## Architecture

The code is layered by responsibility so each part can be understood,
tested, and changed independently:

```
src/
├── Main.java              Console UI / menu — no business logic, only I/O
├── model/                 Plain data classes (POJOs)
│   ├── Student.java
│   ├── Company.java
│   ├── Job.java
│   ├── Application.java
│   ├── Interview.java
│   ├── ApplicationStatus.java   (enum)
│   └── InterviewResult.java     (enum)
├── dao/                   Data Access Objects — the ONLY classes that write SQL
│   ├── StudentDAO.java
│   ├── CompanyDAO.java
│   ├── JobDAO.java
│   ├── ApplicationDAO.java
│   └── InterviewDAO.java
├── service/                Business logic, built only on models + DAOs
│   ├── EligibilityService.java   Core eligibility rule engine
│   ├── PlacementService.java     Apply / interview / selection workflow
│   └── ReportService.java        Statistics and summary reports
├── db/
│   └── DBConnection.java   Singleton JDBC connection manager
└── util/
    └── InputValidator.java  Re-prompting console input helper
schema.sql                 MySQL DDL — run once to create the database
```

### Why this design

- **Separation of concerns (layered architecture):** UI (`Main`) → business
  rules (`service`) → persistence (`dao`) → database. Each layer only talks
  to the one below it, so, for example, the eligibility rules can be
  changed without touching any SQL, and the storage engine could be
  swapped without touching business logic.
- **Encapsulation:** every model class keeps its fields private and
  exposes them only through getters/setters, so validation and formatting
  logic has one place to live.
- **Single Responsibility:** each DAO handles exactly one table; each
  service handles exactly one concern (eligibility, workflow, or
  reporting).
- **PreparedStatement everywhere:** all SQL is parameterized, which
  prevents SQL injection and lets the JDBC driver cache query plans.
- **Fail-fast startup:** `Main` opens the DB connection immediately on
  launch so configuration problems surface before any menu is shown.

## Core feature: eligibility analysis

`EligibilityService.isEligible(Student, Job)` checks four independent
criteria — CGPA, backlog count, department, and required skills — and
combines them with logical AND. Because each rule is a private method,
you can add a new criterion (e.g. a minimum semester, or a "no active
offer" rule) by adding one more private check and one more term in
`isEligible`, without touching anything else.

`explainEligibility` reuses the same private checks to produce a
line-by-line PASS/FAIL breakdown, so a student can see exactly which
condition is blocking them from a role.

## Selection workflow

1. A student applies (`PlacementService.apply`) — this re-validates
   eligibility and rejects duplicate applications before touching the DB.
2. A placement officer can shortlist an application, schedule one or more
   interview rounds, and record PASS/FAIL/PENDING for each round.
3. `finalizeSelection` looks at every recorded round for an application:
   any FAIL → REJECTED; all PASS → SELECTED; anything still PENDING →
   status is left unchanged until every round is resolved.

## Reports

`ReportDAO` computes every statistic with SQL directly — `COUNT`, `MAX`,
and `GROUP BY` — rather than fetching entire tables into Java and
summing them in loops. `ReportService` just calls it and formats the
results. This keeps report generation fast and constant-memory
regardless of how many placement records accumulate.

## Performance notes

Two query paths are deliberately split between what SQL does well and
what only Java can do cleanly here:

- **Eligibility candidate search** (`StudentDAO.findCandidatesByCriteria`,
  `JobDAO.findJobsMatchingStudent`): CGPA, backlog, and department checks
  are pushed into the `WHERE` clause, so the database — not a Java loop —
  eliminates the vast majority of non-matching rows before anything
  crosses the JDBC boundary. Only the skill check, which can't be
  expressed as a safe SQL predicate over a free-text comma-separated
  column without normalizing the schema, still runs in Java — but only
  against the small set of rows that already passed everything else.
- **Reporting** (`ReportDAO`): totals, highest package, and department-/
  company-wise selection counts are all single aggregate SQL queries
  instead of full-table scans in application code.

If the `skills` column becomes a bottleneck at larger scale, the natural
next step is to normalize it into a `student_skills(student_id, skill)`
junction table (and similarly for `job_required_skills`), which would let
skill matching become a SQL join/`HAVING COUNT(...)` as well.

## Setup

### 1. Requirements

- JDK 17 or later (the code uses text blocks and switch expressions). Check with `java -version` and `javac -version`.
- MySQL Server 8 (running) and the MySQL command-line client or MySQL Workbench.
- The MySQL Connector/J driver. It is already included in this repository as `lib/mysql-connector-j-26.10.0.jar`.

### 2. Create the database

Open Command Prompt in the project folder (the folder that contains `schema.sql`) and run the script as the MySQL root user:

```cmd
mysql -u root -p < schema.sql
```

This creates the `placement_system` database with its five tables (students, companies, jobs, applications, interviews) and indexes. If `mysql` is not recognised, add the MySQL `bin` folder to your PATH, or open `schema.sql` in MySQL Workbench and run it there.

### 3. Create a MySQL user for the application (recommended)

Log in to MySQL as root (`mysql -u root -p`) and run:

```sql
CREATE USER 'OOPS_User'@'localhost' IDENTIFIED BY 'choose_a_password';
GRANT ALL PRIVILEGES ON placement_system.* TO 'OOPS_User'@'localhost';
FLUSH PRIVILEGES;
```

You can use any user name and password. You can also use `root` directly.

### 4. Set the connection details

Open `src/db/DBConnection.java` and replace the placeholders with your own values:

```java
private static final String DB_USER = "OOPS_User";          // your MySQL user
private static final String DB_PASSWORD = "choose_a_password"; // your MySQL password
```

The URL is `jdbc:mysql://localhost:3306/placement_system?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`. Change `localhost` or `3306` only if your MySQL server runs somewhere else. Do not commit your real password to GitHub.

### 5. Compile and run (Windows Command Prompt)

```cmd
dir /s /b src\*.java > sources.txt
javac -cp "lib\*" -d out @sources.txt
java -cp "out;lib\*" Main
```

On Linux or macOS use `:` instead of `;`, and `find src -name "*.java" > sources.txt`:

```bash
find src -name "*.java" > sources.txt
javac -cp "lib/*" -d out @sources.txt
java -cp "out:lib/*" Main
```

### 6. Run from VS Code or IntelliJ (optional)

Open the project folder, make sure `lib/mysql-connector-j-26.10.0.jar` is on the project's classpath (VS Code: Java Projects, Referenced Libraries, add the jar; IntelliJ: Project Structure, Libraries, add the jar), then run `Main.java`.

### 7. Build a runnable JAR (optional)

This needs the JDK's `bin` folder on your PATH (so that `jar` is recognised). After step 5:

```cmd
cd out
jar xf ..\lib\mysql-connector-j-26.10.0.jar
cd ..
jar cfe placement.jar Main -C out .
java -jar placement.jar
```

The MySQL server must be running whenever you start the program. Run `java -jar placement.jar` from the project folder.

### Common errors

| Error | Cause and fix |
| --- | --- |
| `ClassNotFoundException: com.mysql.cj.jdbc.Driver` | The Connector/J jar is not on the classpath. Add `lib\*` to `-cp`. |
| `Public Key Retrieval is not allowed` | Keep `allowPublicKeyRetrieval=true` in the URL. |
| `Access denied for user ... (using password: YES)` | `DB_USER` or `DB_PASSWORD` in `DBConnection.java` is wrong. |
| `Access denied for user ... to database 'placement_system'` | Run the `GRANT` statement in step 3 and make sure `schema.sql` was run. |
| `'jar' is not recognized` | Add the JDK `bin` folder (not just the JRE or javapath shim) to PATH. |

## Suggested demo flow

1. Add 2–3 companies, then a job under each with different CGPA/backlog/
   department/skill requirements (Job Management).
2. Add several students with varying CGPA, backlogs, and skills (Student
   Management).
3. Run Eligibility Analysis → "Find eligible students for a job" to see
   the rule engine at work, and "Explain eligibility" for a rejected
   student to see exactly why.
4. Apply on behalf of an eligible student, shortlist, schedule an
   interview, record a PASS result, then finalize selection.
5. Open Placement Reports to see the aggregated summary update.

## Possible extensions

- Add authentication so students and placement officers see different
  menus (role-based access).
- Add a package/CTC-based ranking so a student is shown their
  highest-paying eligible offer first.
- Export the summary report to CSV/PDF.
- Replace the console UI with a JavaFX or Spring Boot web front-end —
  because business logic lives entirely in `service/`, this only means
  writing a new UI layer, not rewriting the rules.
