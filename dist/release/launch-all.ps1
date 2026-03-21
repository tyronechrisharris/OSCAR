<#
Windows PowerShell script to start OSCAR stack deterministically:
  1) start postgis and wait for pg_isready + verification that DB 'gis' exists
  2) start osh and wait until it is running (or fail early on datastore errors)
  3) start caddy last
#>

param(
  [int]$OshWaitSeconds = 240
)

# Determine script location
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# 1. Look for docker-compose.yml in the same directory (standalone release)
if (Test-Path "$scriptDir\docker-compose.yml") {
    $releaseRoot = $scriptDir
    $composeFile = "$releaseRoot\docker-compose.yml"
# 2. Look for it two levels up (standard dev repo structure)
} elseif (Test-Path "$scriptDir\..\..\docker-compose.yml") {
    $releaseRoot = (Get-Item "$scriptDir\..\..").FullName
    $composeFile = "$releaseRoot\docker-compose.yml"
} else {
    Write-Error "Error: Could not find docker-compose.yml in $scriptDir or repo root."
    exit 1
}

Set-Location $releaseRoot

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Log { param($m) Write-Host "[$(Get-Date -Format o)] $m" }

Write-Log "OSCAR deterministic startup (Windows/PowerShell): PostGIS -> OSH -> Caddy"
Write-Log "Using compose file: $composeFile"

# Ensure necessary directories exist for runtime mounts
$oshDir = "osh-node-oscar"
if (-not (Test-Path "$oshDir\trusted_certificates")) { New-Item -ItemType Directory -Path "$oshDir\trusted_certificates" | Out-Null }
if (-not (Test-Path "$oshDir\rules")) { New-Item -ItemType Directory -Path "$oshDir\rules" | Out-Null }

# 1) Start PostGIS
Write-Log "Starting PostGIS..."
& docker compose -f $composeFile up -d postgis

# Wait for pg_isready (TCP)
Write-Host -NoNewline "Waiting for PostGIS (pg_isready)"
while ($true) {
  try {
    $rc = & docker exec -u postgres postgis pg_isready -d gis -U postgres -h localhost > $null 2>&1
    if ($LASTEXITCODE -eq 0) { Write-Host " OK"; break }
  } catch {
    # ignore and retry
  }
  Write-Host -NoNewline "."
  Start-Sleep -Seconds 2
}

# Wait until database 'gis' exists
Write-Host -NoNewline "Waiting for 'gis' database to exist"
while ($true) {
  try {
    $out = & docker exec -u postgres postgis psql -tAc "SELECT 1 FROM pg_database WHERE datname='gis'" 2>$null
    if ($out -match "1") { Write-Host " OK"; break }
  } catch {
    # ignore and wait
  }
  Write-Host -NoNewline "."
  Start-Sleep -Seconds 2
}

# 2) Start OSH and watch startup
Write-Log "Starting OSH backend..."
& docker compose -f $composeFile up -d osh

Write-Log "Waiting for OSH to become stable..."
$endTime = (Get-Date).AddSeconds($OshWaitSeconds)
while ((Get-Date) -lt $endTime) {
  try {
    $state = (& docker inspect --format '{{.State.Status}}' osh 2>$null).Trim()
  } catch {
    $state = "missing"
  }

  if ($state -eq "exited" -or $state -eq "dead") {
    Write-Log "OSH container exited unexpectedly — showing last 300 lines of logs:"
    & docker logs --tail 300 osh 2>&1 | ForEach-Object { Write-Host $_ }
    Write-Error "OSH exited — aborting startup."
    exit 2
  }

  if ($state -eq "running") {
    # check logs for datastore errors
    $logs = & docker logs osh --tail 200 2>&1
    if ($logs -match "Error starting datastores" -or $logs -match "Fatal error during sensorhub execution") {
      Write-Host "OSH reported datastore startup error. Showing last 300 lines of logs:"
      & docker logs --tail 300 osh 2>&1 | ForEach-Object { Write-Host $_ }
      Write-Error "OSH datastore error — aborting."
      exit 2
    }

    # assume OK after short grace
    Start-Sleep -Seconds 5
    Write-Log "OSH appears to be running."
    break
  }

  Write-Host -NoNewline "."
  Start-Sleep -Seconds 2
}

if ((Get-Date) -ge $endTime) {
  Write-Log "Timed out waiting for OSH to become stable. Showing last 300 lines of logs:"
  & docker logs --tail 300 osh 2>&1 | ForEach-Object { Write-Host $_ }
  throw "Timeout waiting for OSH"
}

# 3) Start Caddy last
Write-Log "Starting Caddy (last)..."
# Set defaults
if ([string]::IsNullOrEmpty($env:DEPLOYMENT_PROFILE)) { $env:DEPLOYMENT_PROFILE = "federated" }
if ([string]::IsNullOrEmpty($env:DOMAIN)) { $env:DOMAIN = "localhost" }

& docker compose -f $composeFile up -d caddy

Write-Log "OSCAR stack startup complete. Access the OSH Backend via Caddy on ports 80/443."
