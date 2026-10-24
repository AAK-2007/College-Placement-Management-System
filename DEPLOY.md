# Hosting the website online (Render + Aiven, free)

- **Aiven** hosts the MySQL database (free plan).
- **Render** runs the web app from this GitHub repository using the `Dockerfile` (free plan).
- Both accept sign-up with email and password. No code changes are needed: everything is set
  with environment variables in Render.

Time needed: about 30 minutes, most of it waiting for the first build.

> Free-plan limits: the Render app goes to sleep after about 15 minutes without visitors, so the
> first page after a quiet period takes up to a minute to load. Aiven may power off a free
> database that is not used for a long time (it emails you first).

---

## Step 1 — Create the database on Aiven

1. Sign up at **https://console.aiven.io**.
2. **Create service** → **MySQL** → plan **Free** → choose a region close to you → **Create service**.
   Wait until the status is **Running** (a few minutes).
3. On the service's **Overview** page, note the **Connection information**:
   **Host**, **Port**, **User** (`avnadmin`) and **Password**.

## Step 2 — Create the tables

From your PC, in PowerShell (replace the values from step 1.3):

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -h <host> -P <port> -u avnadmin -p --ssl-mode=REQUIRED
```

Enter the Aiven password. At the `mysql>` prompt, using the full path to this folder with
forward slashes:

```sql
SOURCE C:/path/to/placement-web/sql/schema.sql;
```

Optional demo data (made-up students, companies and jobs):

```sql
SOURCE C:/path/to/placement-web/sql/sample-data.sql;
```

Type `exit` to leave. This creates the `placement_system` database with all six tables.

## Step 3 — Make a new officer password

The default officer password is written in the README, so a public site must use its own.
On your PC, in the `placement-web` folder:

```powershell
.\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.arguments=--hash-password=YourStrongPassword"
```

Copy the long value after `OFFICER_PASSWORD_HASH=` (it starts with `$2a$10$`).

## Step 4 — Create the web service on Render

1. Sign up at **https://dashboard.render.com**.
2. **New** → **Web Service** → **Public Git Repository**, and paste the repository address
   (for example `https://github.com/AAK-2007/College-Placement-Management-System`) → **Connect**.
3. Fill in:
   - **Branch:** `Live-Deploy` (the branch that contains this web app and its `Dockerfile`)
   - **Root Directory:** leave empty if this web app is at the top of the branch; otherwise the folder name, e.g. `placement-web`
   - **Language / Runtime:** **Docker** (Render detects the `Dockerfile`)
   - **Instance Type:** **Free**
4. Under **Environment Variables**, add:

   | Key | Value |
   |---|---|
   | `DB_URL` | `jdbc:mysql://<host>:<port>/placement_system?sslMode=REQUIRED&serverTimezone=UTC` |
   | `DB_USER` | `avnadmin` |
   | `DB_PASSWORD` | the Aiven password |
   | `OFFICER_PASSWORD_HASH` | the hash from step 3 |
   | `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `5` (keeps within the free database's connection limit) |

   Do not set `PORT`: Render provides it, and the app uses it automatically.
5. **Create Web Service**. The first build takes 5–10 minutes.

## Step 5 — Check it

1. In Render's **Logs**, wait for `Started PlacementWebApplication`.
2. Open the address Render shows (`https://<name>.onrender.com`).
3. Log in as `officer` with the password from step 3. Students and companies can now sign up
   from the home page.

Every later `git push` to the `Live-Deploy` branch redeploys the site automatically.

---

## What the Dockerfile sets up

| Setting | Why |
|---|---|
| Listens on `0.0.0.0` and on Render's `$PORT` | So the host can reach the app (on your PC it only listens to `localhost`) |
| `SERVER_FORWARD_HEADERS_STRATEGY=framework` | Render serves https in front of the app; this keeps links and redirects on https |
| `SPRING_THYMELEAF_CACHE=true` | Pages are cached instead of re-read on every request |
| `-XX:MaxRAMPercentage=75` | Uses most of the free plan's memory without exceeding it |
| Runs as a normal user, tests skipped in the build | Safer, and a faster cloud build (run the tests on your PC with `.\mvnw.cmd test`) |

The same image runs on any host that supports Docker, for example:

```powershell
docker build -t placement-web .
```
```powershell
docker run -p 8080:8080 -e DB_URL="jdbc:mysql://..." -e DB_USER=... -e DB_PASSWORD=... -e OFFICER_PASSWORD_HASH='...' placement-web
```

## Troubleshooting

| Problem | Fix |
|---|---|
| Build fails on Render | Check **Branch** is `Live-Deploy` and **Runtime** is Docker; the `Dockerfile` must be in the folder Render builds (see Root Directory). |
| Dashboard says "MySQL rejected the user name or password" | `DB_USER` / `DB_PASSWORD` do not match Aiven's connection information. |
| "The placement_system database does not exist yet" | Step 2 was not run (or ran against another server). |
| "Could not connect to the database" | Wrong host or port in `DB_URL`, the Aiven service is powered off, or `sslMode=REQUIRED` is missing. |
| Officer login fails | `OFFICER_PASSWORD_HASH` must be the whole hash, starting with `$2a$10$`, with no spaces. |
| First page takes about a minute | Normal on the free plan: the app was asleep. |
