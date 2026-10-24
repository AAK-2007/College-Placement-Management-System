# College Placement System — Web Version

A localhost web version of the **College Placement Management and Eligibility Analysis System**.
It uses the same business rules as the console project in `..\OOPS` (which is unchanged), with
Spring Boot, Thymeleaf pages styled with Bootstrap, a login for the placement officer, and a
pooled MySQL connection.

- Runs at **http://localhost:8080** (only reachable from this computer).
- Three kinds of users, each seeing only their own part of the site ([details](#who-can-do-what)):
  the **placement officer**, **students** and **companies**. Students and companies sign up
  themselves from the home page.
- Default officer login: **`officer` / `Placement@2026`** — change it before real use ([how](#change-the-officer-login)).
- To put it online, see **[DEPLOY.md](DEPLOY.md)** (Render + Aiven, free, using the `Dockerfile`).

---

## Quick start (Windows)

You need **JDK 17 or newer** and **MySQL Server 8** (running). Maven is **not** needed: the
project includes the Maven Wrapper (`mvnw.cmd`), which downloads Maven by itself on first run.
The first run also downloads the libraries, so it needs internet and takes a few minutes.

Open **PowerShell** in the `placement-web` folder, then:

```powershell
.\setup-database.ps1
```

It asks (in hidden prompts) for your **MySQL root password** and a **new password for the app's
MySQL user** `OOPS_User`. It creates or resets that user, gives it access to `placement_system`,
and creates the tables from `sql\schema.sql` if the database is empty. Existing data is never touched.

```powershell
.\run.ps1
```

It asks for the MySQL user (press Enter for `OOPS_User`) and its password, then starts the app.
Open http://localhost:8080 when you see `Started PlacementWebApplication`. Stop it with **Ctrl+C**.

The steps below do the same thing by hand.

---

## Setup step by step

### 1. Check Java and MySQL

```powershell
java -version
```
```powershell
Get-Service MySQL80
```

Java must be 17 or newer. The MySQL service should be `Running` (start it with
`Start-Service MySQL80` from an Administrator PowerShell, or from the Services app).

The MySQL command-line client is at
`C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`. If `mysql` is not recognised, use that
full path, as below, or add the `bin` folder to PATH.

### 2. Create the MySQL user

Log in as root:

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p
```

At the `mysql>` prompt (choose your own password):

```sql
CREATE USER IF NOT EXISTS 'OOPS_User'@'localhost' IDENTIFIED BY 'choose_a_password';
ALTER USER 'OOPS_User'@'localhost' IDENTIFIED BY 'choose_a_password';
GRANT ALL PRIVILEGES ON placement_system.* TO 'OOPS_User'@'localhost';
FLUSH PRIVILEGES;
```

### 3. Create the tables (and optional sample data)

Still at the `mysql>` prompt, using the full path to your `placement-web` folder (with forward slashes):

```sql
SOURCE C:/path/to/placement-web/sql/schema.sql;
```

Skip `schema.sql` if `placement_system` already has its tables (for example, from the console
version) — it would stop at "Duplicate key name" because the indexes already exist.

The web version adds a sixth table, `users` (login accounts for students and companies). You do
not need to create it: the app creates it at startup if it is missing
(`src/main/resources/db/users-table.sql`). It is also in `sql/schema.sql` for new databases.

To load made-up students, companies and jobs into an **empty** database:

```sql
SOURCE C:/path/to/placement-web/sql/sample-data.sql;
```

Type `exit` to leave MySQL.

### 4. Set the database environment variables

The app reads the database settings from environment variables; no password is stored in the project.

| Variable | Meaning | Default |
|---|---|---|
| `DB_USER` | MySQL user | *(none — required)* |
| `DB_PASSWORD` | MySQL password | *(none — required)* |
| `DB_URL` | JDBC URL | `jdbc:mysql://localhost:3306/placement_system?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `OFFICER_USERNAME` | Web login user name | `officer` |
| `OFFICER_PASSWORD_HASH` | BCrypt hash of the web login password | hash of `Placement@2026` |

**For the current PowerShell window only** (recommended; `run.ps1` does this for you):

```powershell
$env:DB_USER = "OOPS_User"
```
```powershell
$env:DB_PASSWORD = "your_password"
```

**Permanently for your Windows account** (then open a new terminal). Note that this stores the
password in plain text in your user environment:

```powershell
setx DB_USER "OOPS_User"
```
```powershell
setx DB_PASSWORD "your_password"
```

In **Command Prompt** (cmd) the per-window form is `set DB_USER=OOPS_User`. In PowerShell,
`set` does **not** set environment variables — use `$env:` as above.

### 5. Run the app

```powershell
.\mvnw.cmd spring-boot:run
```

Open **http://localhost:8080** and log in as `officer` / `Placement@2026`.
The dashboard shows the placement summary; if the database is not reachable it says why
(wrong password, missing database, MySQL not running).

`mvn spring-boot:run` also works if you install Maven yourself.

### Change the officer login

The password is only ever stored as a BCrypt hash. To make a hash for a new password:

```powershell
.\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.arguments=--hash-password=MyNewPassword"
```

It prints `OFFICER_PASSWORD_HASH=$2a$10$...` and exits. Set that value (single quotes, because
the hash contains `$`) before starting the app:

```powershell
$env:OFFICER_PASSWORD_HASH = '$2a$10$...paste the hash here...'
```

`OFFICER_USERNAME` changes the user name the same way.

---

## Running the tests

```powershell
.\mvnw.cmd test
```

77 JUnit 5 tests cover the service rules and who can open what. They use Mockito in place of the
database, so MySQL does not need to be running:

| Test class | What it checks |
|---|---|
| `service.EligibilityServiceTest` | CGPA / backlog boundaries, department list and ALL, partial skill match (at least one), no skills required, case and spaces ignored, SQL pushdown arguments, ranking by matched skills, PASS/FAIL explanation |
| `service.PlacementServiceTest` | Apply (all five outcomes, duplicate caught by the unique key); shortlist only from APPLIED; interviews only for SHORTLISTED / INTERVIEW_SCHEDULED; results frozen once decided; finalize (any FAIL → REJECTED, PENDING or no rounds → unchanged, all PASS → SELECTED, decided never moves backwards) |
| `util.FormValidatorTest` | CGPA 0–10, backlogs ≥ 0, package 0–1000, required/optional text, "empty keeps current value", both date formats, impossible dates |
| `web.controller.JobControllerTest` | How eligible departments are stored (upper case, ALL) |
| `web.AccessControlTest` | Logs in as a visitor, student, company and officer: blocked pages return "forbidden", and a student or company cannot reach another student's or company's records even by changing ids in the address or form |

---

## Who can do what

The home page (`/`) is public: it explains the system and links to log in and to the two sign-up
pages. After logging in, each user gets their own dashboard and menu.

| Section | Placement officer | Student | Company |
|---|---|---|---|
| **Dashboard** | Placement summary report | Own profile, jobs they are eligible for, own applications | Own company, jobs and application figures |
| **Students** | List, add, edit, delete | Only **My profile** (view and edit; register number fixed) | — |
| **Companies** | List, add, delete | View the list (read only) | — (own details on the dashboard) |
| **Jobs** | All jobs, add for any company | View all openings (read only), with Check eligibility and Apply | Only **own** jobs; post new jobs for itself |
| **Eligibility** | All three views for any student or job | Only their **own**: eligible jobs and PASS/FAIL per job | — |
| **Applications** | All; apply for any student; shortlist | Apply for themselves; see **own** applications and status | Only applications to **own** jobs; shortlist |
| **Interviews** | All applications | — | Only applications to **own** jobs: schedule rounds, record PASS/FAIL, finalize |

How it is enforced:

- **Page rules** in `web/config/SecurityConfig.java` decide which roles may open which addresses.
  Opening anything else shows "You do not have access to that page" (HTTP 403).
- **Ownership checks** in the controllers: a student's id and a company's id come from their login,
  never from the form or address. A student who sends someone else's id still only applies for or
  sees themselves; a company that opens another company's application gets the 403 page.
- The menu only shows the sections the role can use.

### Accounts

- **Students** sign up at *Register as a student* with a user name, password and their academic
  profile (same rules as the officer's Add student form). **Companies** sign up at *Register a
  company* with a user name, password and company details. Each sign-up creates the record and a
  login linked to it.
- User names are unique (the officer's user name is reserved); passwords need at least 8
  characters and are stored only as BCrypt hashes in the `users` table.
- Students and companies added by the officer, or from the console version, have **no login**;
  they remain managed by the officer.
- Deleting a student or company (officer only) also deletes its login.

Errors never show a stack trace: database problems show a plain message, and form mistakes are
highlighted on the form with the same messages as the console version.

---

## Business rules (same as the console version)

- **Eligibility:** CGPA ≥ job minimum, backlogs ≤ job maximum, department in the job's list (or ALL),
  and at least **one** required skill (no skills required = pass). Results are sorted by number of
  matching skills, best first. CGPA, backlogs and department are filtered in SQL; only the skill
  check runs in Java.
- **Apply:** the student and job must exist, the student must not have applied already, and must be eligible.
- **Status flow:** APPLIED → SHORTLISTED (only from APPLIED) → INTERVIEW_SCHEDULED (rounds can be added only
  to SHORTLISTED or INTERVIEW_SCHEDULED applications) → SELECTED or REJECTED.
- **Finalize:** any FAIL → REJECTED; any PENDING or no rounds → unchanged; all PASS → SELECTED.
  Decided applications never move backwards.
- **Validation:** CGPA 0–10, backlogs ≥ 0, package 0–1000, unique register number, valid interview
  date (empty = today); email, phone, sector and skills are optional.
- **Delete:** deleting a student or company also deletes their applications and interviews
  (`ON DELETE CASCADE` in `schema.sql`), after a confirmation page.

### Differences from the console version

- **Three roles with their own logins** (officer, student, company) and a new `users` table, as
  described in [Who can do what](#who-can-do-what).
- **Connection pool.** `DBConnection` hands out connections from a HikariCP pool (max 10). Every DAO
  method borrows one and closes it, which returns it to the pool. The SQL itself is unchanged.
- **Decided applications are fully frozen.** The console allowed changing a round's result after the
  decision and finalizing again, which could turn REJECTED into SELECTED. Now recording a result
  needs an INTERVIEW_SCHEDULED application, only PASS or FAIL can be recorded, and finalize never
  changes a SELECTED/REJECTED application.
- **Services return data.** `EligibilityService.explainEligibility` returns a PASS/FAIL report and the
  eligible lists include the matched-skill count; `ReportService.printSummary` became `getSummary()`.
- **Stored text is tidied.** Departments are saved in upper case. A job's departments are saved as
  e.g. `CSE,IT`, and any list containing ALL is saved as just `ALL` (the SQL filter only recognises
  ALL as the whole value). Blank optional fields are saved as empty (NULL).
- **Extra read-only queries** for display: application lists with names, counts for the delete
  confirmation pages, and total students / companies / jobs as `COUNT(*)`.

---

## Project layout

```
placement-web/
├── pom.xml                      Spring Boot 3.5 (web, thymeleaf, security, jdbc), MySQL driver, tests
├── mvnw, mvnw.cmd, .mvn/        Maven Wrapper
├── setup-database.ps1           Creates/resets the MySQL user and creates the tables
├── run.ps1                      Asks for the DB login and starts the app
├── sql/
│   ├── schema.sql               The console version's schema.sql plus the users table
│   └── sample-data.sql          Made-up students, companies and jobs
└── src/
    ├── main/java/
    │   ├── model/               Copied unchanged from the console version
    │   ├── dao/                 Console DAOs using pooled connections (+ display queries), UserDAO
    │   ├── service/             Eligibility / Placement / Report services
    │   ├── db/DBConnection      Wraps the HikariCP pool
    │   ├── util/FormValidator   Web replacement for the console InputValidator
    │   └── web/                 Spring Boot app, config (beans, page rules), controllers, forms,
    │       │                    friendly error handling
    │       └── security/        Logged-in user (AppUser), roles, account lookup, ownership checks
    ├── main/resources/
    │   ├── application.properties   Settings; database values come from environment variables
    │   ├── db/users-table.sql       Creates the users table at startup if missing
    │   ├── templates/               Thymeleaf pages (home/ = welcome and dashboards, register/ = sign-up)
    │   └── static/css/app.css       Site theme
    └── test/java/               JUnit tests
```

Bootstrap is loaded from the jsDelivr CDN, so pages need internet to look styled; they still work
without it.

---

## Troubleshooting

| Message | Cause and fix |
|---|---|
| Browser: "localhost refused to connect" | The app is not running. Start it (`.\run.ps1`) and wait for `Started PlacementWebApplication`. |
| "You do not have access to that page" | That page is not part of your role (see [Who can do what](#who-can-do-what)), or the record belongs to another student or company. |
| Login: "the database cannot be reached" | MySQL is down or `DB_USER` / `DB_PASSWORD` are wrong; student and company logins need the database. |
| "MySQL rejected the user name or password" | `DB_USER` / `DB_PASSWORD` are wrong. Run `.\setup-database.ps1` to reset the `OOPS_User` password, then `.\run.ps1`. |
| "The placement_system database does not exist yet" | Run `schema.sql` (step 3) or `.\setup-database.ps1`. |
| "Could not connect to the database" | MySQL is not running: `Start-Service MySQL80` (as Administrator). |
| `setup-database.ps1`: "MySQL did not accept the root password" | The root password is wrong. If it is lost, see [Reset the MySQL root password](#reset-the-mysql-root-password). |
| `mvnw : The term 'mvnw' is not recognized` | In PowerShell, run it as `.\mvnw.cmd` from inside the `placement-web` folder. |
| "running scripts is disabled on this system" | Run `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` once, or start the script with `powershell -ExecutionPolicy Bypass -File .\run.ps1`. |
| "Port 8080 was already in use" | The app (or another program) is already running. Stop it with Ctrl+C in its window, or close that program. |
| `schema.sql`: "Duplicate key name" | The tables already exist; you do not need to run `schema.sql` again. |

### Reset the MySQL root password

In an **Administrator** PowerShell (replace `NewRootPass1`):

```powershell
Stop-Service MySQL80
```
```powershell
Set-Content C:\mysql-init.txt "ALTER USER 'root'@'localhost' IDENTIFIED BY 'NewRootPass1';"
```
```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe" --defaults-file="C:\ProgramData\MySQL\MySQL Server 8.0\my.ini" --init-file=C:\mysql-init.txt --console
```

When it prints "ready for connections", press Ctrl+C and wait for it to stop. Then:

```powershell
Remove-Item C:\mysql-init.txt
```
```powershell
Start-Service MySQL80
```
