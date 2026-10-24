# One-time MySQL setup for the placement web app.
#
# - Creates the MySQL user the app logs in with (default OOPS_User), or resets
#   its password if it already exists, and gives it access to placement_system.
# - Creates the placement_system database and its tables from sql\schema.sql,
#   but only if the database has no tables yet (existing data is never touched).
#
# Passwords are typed into hidden prompts. They are kept only in temporary files
# that are deleted when the script finishes; nothing is saved in the project.
#
# Run from the placement-web folder:   .\setup-database.ps1

param(
    [string]$AppUser = "OOPS_User"
)

$ErrorActionPreference = "Stop"

function Get-PlainText([System.Security.SecureString]$secure) {
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

# ---- Find mysql.exe ----
$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if (-not (Test-Path $mysql)) {
    $cmd = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $cmd) {
        Write-Host "Could not find mysql.exe. Install MySQL Server 8 or add its bin folder to PATH." -ForegroundColor Red
        exit 1
    }
    $mysql = $cmd.Source
}
$schema = Join-Path $PSScriptRoot "sql\schema.sql"

# ---- Ask for passwords (hidden) ----
Write-Host ""
Write-Host "Step 1 of 2: the MySQL root password (the one you chose when installing MySQL)."
$rootPassword = Get-PlainText (Read-Host "MySQL root password" -AsSecureString)

Write-Host ""
Write-Host "Step 2 of 2: choose a password for the app's MySQL user '$AppUser'."
Write-Host "If '$AppUser' already exists, its password is changed to this one."
$appPassword = Get-PlainText (Read-Host "New password for $AppUser" -AsSecureString)
$again = Get-PlainText (Read-Host "Type it again" -AsSecureString)
if ($appPassword -ne $again) {
    Write-Host "The two passwords do not match. Nothing was changed." -ForegroundColor Red
    exit 1
}
if ([string]::IsNullOrEmpty($appPassword)) {
    Write-Host "The password cannot be empty. Nothing was changed." -ForegroundColor Red
    exit 1
}

# ---- Temporary files (deleted in finally) ----
$id = [guid]::NewGuid().ToString("N")
$optionFile = Join-Path $env:TEMP "placement-setup-$id.cnf"
$sqlFile = Join-Path $env:TEMP "placement-setup-$id.sql"

try {
    # The root password goes in a MySQL option file instead of the command line,
    # so it does not show up in the process list.
    # Files are written as UTF-8 without a byte-order mark, which the mysql client expects.
    $utf8 = New-Object System.Text.UTF8Encoding $false
    $rootEscaped = $rootPassword.Replace('\', '\\').Replace('"', '\"')
    [IO.File]::WriteAllText($optionFile, "[client]`r`nuser=root`r`npassword=`"$rootEscaped`"`r`n", $utf8)

    # 1) Check the root login and count existing tables.
    $count = & $mysql "--defaults-extra-file=$optionFile" -N -B -e `
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'placement_system'"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "MySQL did not accept the root password (see the message above). Nothing was changed." -ForegroundColor Red
        exit 1
    }
    $tableCount = [int]("$count".Trim())

    # 2) Create the database and the app user (or reset its password).
    $appEscaped = $appPassword.Replace('\', '\\').Replace("'", "''")
    $setupSql = @"
CREATE DATABASE IF NOT EXISTS placement_system;
CREATE USER IF NOT EXISTS '$AppUser'@'localhost' IDENTIFIED BY '$appEscaped';
ALTER USER '$AppUser'@'localhost' IDENTIFIED BY '$appEscaped';
GRANT ALL PRIVILEGES ON placement_system.* TO '$AppUser'@'localhost';
FLUSH PRIVILEGES;
"@
    [IO.File]::WriteAllText($sqlFile, $setupSql, $utf8)
    & $mysql "--defaults-extra-file=$optionFile" -e "source $($sqlFile.Replace('\', '/'))"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Creating the user failed (see the message above)." -ForegroundColor Red
        exit 1
    }
    Write-Host "MySQL user '$AppUser' is ready and can use the placement_system database." -ForegroundColor Green

    # 3) Create the tables only if the database is empty.
    if ($tableCount -eq 0) {
        & $mysql "--defaults-extra-file=$optionFile" -e "source $($schema.Replace('\', '/'))"
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Creating the tables failed (see the message above)." -ForegroundColor Red
            exit 1
        }
        Write-Host "Created the placement_system tables from sql\schema.sql." -ForegroundColor Green
    } else {
        Write-Host "placement_system already has $tableCount table(s); left them unchanged."
    }

    Write-Host ""
    Write-Host "Done. Start the app with:  .\run.ps1   (user: $AppUser, and the password you just chose)"
}
finally {
    Remove-Item $optionFile, $sqlFile -ErrorAction SilentlyContinue
    $rootPassword = $null
    $appPassword = $null
    $again = $null
}
