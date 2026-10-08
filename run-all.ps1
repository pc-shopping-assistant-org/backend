# Launches the PC Shopping backend: infra (Docker), build, then every service in its own window.
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

$EurekaPort = 8761
$PortWaitTimeoutSeconds = 180
$PortPollIntervalSeconds = 3
$ServiceStartDelaySeconds = 5
$BusinessServices = @('identity-service', 'catalog-service', 'order-service', 'payment-service', 'promotion-service', 'search-service', 'media-service')

function Wait-ForPort([int]$Port, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-NetConnection -ComputerName localhost -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue) { return }
        Start-Sleep -Seconds $PortPollIntervalSeconds
    }
    throw "Port $Port did not open within $TimeoutSeconds seconds."
}

function Start-Service([string]$Module) {
    Write-Host "Starting $Module ..."
    # Child windows inherit the environment variables loaded below. Each service also writes
    # logs/<module>.log (lines carry the traceId), so one traceId can be grepped across all services.
    Start-Process powershell -WorkingDirectory $PSScriptRoot -ArgumentList '-NoExit', '-Command', "`$Host.UI.RawUI.WindowTitle='$Module'; `$env:LOGGING_FILE_NAME='$PSScriptRoot\logs\$Module.log'; mvn -pl $Module spring-boot:run"
}

Write-Host '1. Starting infrastructure...'
docker compose up -d
if ($LASTEXITCODE -ne 0) { throw 'docker compose failed. Is Docker Desktop running?' }

Write-Host '2. Loading .env...'
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
Get-Content .env | Where-Object { $_ -match '^\s*([^#][^=]*)=(.*)$' } | ForEach-Object {
    Set-Item -Path "Env:$($Matches[1].Trim())" -Value $Matches[2].Trim()
}

Write-Host '3. Building...'
mvn clean install -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }

Write-Host '4. Starting discovery-server...'
Start-Service 'discovery-server'
Wait-ForPort -Port $EurekaPort -TimeoutSeconds $PortWaitTimeoutSeconds

Write-Host '5. Starting business services...'
foreach ($service in $BusinessServices) {
    Start-Service $service
    Start-Sleep -Seconds $ServiceStartDelaySeconds
}

Write-Host '6. Starting api-gateway...'
Start-Service 'api-gateway'

Write-Host 'Done. Gateway: http://localhost:8080  Eureka: http://localhost:8761'
Read-Host 'Press Enter to close this window'
