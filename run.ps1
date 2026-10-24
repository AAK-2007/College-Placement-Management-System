# Starts the placement web app on http://localhost:8080.
#
# Asks for the MySQL user and password (hidden) and keeps them only in this
# terminal's environment variables (DB_USER / DB_PASSWORD) while it runs.
# Press Enter at a prompt to keep a value that is already set in this terminal.
#
# Run from the placement-web folder:   .\run.ps1      Stop the app with Ctrl+C.

$ErrorActionPreference = "Stop"

$defaultUser = if ($env:DB_USER) { $env:DB_USER } else { "OOPS_User" }
$user = Read-Host "MySQL user [$defaultUser]"
$env:DB_USER = if ([string]::IsNullOrWhiteSpace($user)) { $defaultUser } else { $user.Trim() }

$prompt = if ($env:DB_PASSWORD) { "MySQL password for $($env:DB_USER) [Enter = keep current]" } else { "MySQL password for $($env:DB_USER)" }
$secure = Read-Host $prompt -AsSecureString
if ($secure.Length -gt 0) {
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

Write-Host "Starting... open http://localhost:8080 when you see 'Started PlacementWebApplication'."
Set-Location $PSScriptRoot
& .\mvnw.cmd spring-boot:run
