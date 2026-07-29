. (Join-Path $PSScriptRoot 'lib.ps1')
Import-MandalaEnv

Assert-MandalaCommand 'docker'
Assert-MandalaCommand 'npm.cmd'

New-Item -ItemType Directory -Path (Join-Path $script:MandalaRuntimeDir 'logs') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $script:MandalaRepositoryRoot 'mandala/traces/runtime') -Force | Out-Null

Write-MandalaLog 'Starting PostgreSQL, OpenTelemetry Collector and Jaeger.'
Invoke-MandalaCompose up --detach --wait postgres jaeger otel-collector
Wait-MandalaHttp 'http://127.0.0.1:13133/' 'OpenTelemetry Collector' 60
Wait-MandalaHttp 'http://127.0.0.1:16686/' 'Jaeger' 60

if (-not $env:DATABASE_URL) { $env:DATABASE_URL = $env:MANDALA_DB_URL }
if (-not $env:DATABASE_USERNAME) { $env:DATABASE_USERNAME = $env:MANDALA_DB_USERNAME }
if (-not $env:DATABASE_PASSWORD) { $env:DATABASE_PASSWORD = $env:MANDALA_DB_PASSWORD }
if (-not $env:BACKEND_PORT) { $env:BACKEND_PORT = '18080' }
if (-not $env:FRONTEND_PORT) { $env:FRONTEND_PORT = '5173' }
if (-not $env:OTEL_EXPORT_ENABLED) { $env:OTEL_EXPORT_ENABLED = 'true' }
if (-not $env:MANDALA_USE_GLOBAL_OTEL) { $env:MANDALA_USE_GLOBAL_OTEL = 'true' }
if (-not $env:OTEL_EXPORTER_OTLP_TRACES_ENDPOINT) { $env:OTEL_EXPORTER_OTLP_TRACES_ENDPOINT = 'http://localhost:4318/v1/traces' }

$backendPidFile = Join-Path $script:MandalaRuntimeDir 'backend.pid'
$backendJar = Join-Path $script:MandalaRepositoryRoot 'sample-app/backend/build/libs/backend-0.1.0-SNAPSHOT.jar'
if (Test-MandalaPidFile $backendPidFile $backendJar) {
    Write-MandalaLog "Sample backend is already running (PID $((Get-MandalaProcessIdentity $backendPidFile).pid))."
} else {
    Remove-Item -LiteralPath $backendPidFile -Force -ErrorAction SilentlyContinue
    try {
        Invoke-WebRequest -Uri "http://127.0.0.1:$($env:BACKEND_PORT)/" -TimeoutSec 1 -UseBasicParsing | Out-Null
        throw "Backend port $($env:BACKEND_PORT) is already occupied by an unmanaged process. Change BACKEND_PORT in .env."
    } catch {
        if ($_.Exception.Message -like 'Backend port * is already occupied*') { throw }
    }
    $agentPath = Join-Path $script:MandalaToolDir 'opentelemetry-javaagent-2.16.0.jar'
    if (-not (Test-Path -LiteralPath $agentPath)) { throw 'OpenTelemetry Java agent is missing. Run .\scripts\setup.ps1.' }
    Write-MandalaLog 'Building the executable sample backend jar.'
    Push-Location $script:MandalaRepositoryRoot
    try {
        & (Join-Path $script:MandalaRepositoryRoot 'gradlew.bat') --console=plain :sample-app:backend:bootJar
        if ($LASTEXITCODE -ne 0) { throw "Backend bootJar failed with exit code $LASTEXITCODE." }
    } finally { Pop-Location }
    if (-not (Test-Path -LiteralPath $backendJar)) { throw "Backend bootJar was not produced: $backendJar" }
    $env:OTEL_SERVICE_NAME = 'mandala-sample-backend'
    $env:OTEL_TRACES_EXPORTER = 'otlp'
    $env:OTEL_METRICS_EXPORTER = 'none'
    $env:OTEL_LOGS_EXPORTER = 'none'
    $env:OTEL_EXPORTER_OTLP_PROTOCOL = 'http/protobuf'
    $env:OTEL_EXPORTER_OTLP_ENDPOINT = 'http://localhost:4318'
    Write-MandalaLog "Starting sample backend on port $($env:BACKEND_PORT)."
    $backend = Start-Process -FilePath 'java.exe' `
        -ArgumentList @("-javaagent:`"$agentPath`"", '-jar', "`"$backendJar`"") `
        -WorkingDirectory $script:MandalaRepositoryRoot `
        -RedirectStandardOutput (Join-Path $script:MandalaRuntimeDir 'logs/backend.log') `
        -RedirectStandardError (Join-Path $script:MandalaRuntimeDir 'logs/backend-error.log') `
        -PassThru
    Set-MandalaPidFile $backendPidFile $backend $backendJar
}

$frontendPidFile = Join-Path $script:MandalaRuntimeDir 'frontend.pid'
$frontendMarker = 'npm run dev'
if (Test-MandalaPidFile $frontendPidFile $frontendMarker) {
    Write-MandalaLog "Sample frontend is already running (PID $((Get-MandalaProcessIdentity $frontendPidFile).pid))."
} else {
    Remove-Item -LiteralPath $frontendPidFile -Force -ErrorAction SilentlyContinue
    try {
        Invoke-WebRequest -Uri "http://127.0.0.1:$($env:FRONTEND_PORT)/" -TimeoutSec 1 -UseBasicParsing | Out-Null
        throw "Frontend port $($env:FRONTEND_PORT) is already occupied by an unmanaged process. Change FRONTEND_PORT in .env."
    } catch {
        if ($_.Exception.Message -like 'Frontend port * is already occupied*') { throw }
    }
    Write-MandalaLog "Starting sample frontend on port $($env:FRONTEND_PORT)."
    $frontend = Start-Process -FilePath 'npm.cmd' `
        -ArgumentList @('run', 'dev', '--workspace', '@mandala/sample-frontend') `
        -WorkingDirectory $script:MandalaRepositoryRoot `
        -RedirectStandardOutput (Join-Path $script:MandalaRuntimeDir 'logs/frontend.log') `
        -RedirectStandardError (Join-Path $script:MandalaRuntimeDir 'logs/frontend-error.log') `
        -PassThru
    Set-MandalaPidFile $frontendPidFile $frontend $frontendMarker
}

Wait-MandalaHttp "http://127.0.0.1:$($env:BACKEND_PORT)/actuator/health" 'Sample backend' 120
Wait-MandalaHttp "http://127.0.0.1:$($env:FRONTEND_PORT)/" 'Sample frontend' 60
if (-not (Test-MandalaPidFile $backendPidFile $backendJar)) { throw 'Backend process exited or its identity changed. See .runtime/logs/backend-error.log.' }
if (-not (Test-MandalaPidFile $frontendPidFile $frontendMarker)) { throw 'Frontend process exited or its identity changed. See .runtime/logs/frontend-error.log.' }
Write-MandalaLog "Environment is ready. Frontend: http://127.0.0.1:$($env:FRONTEND_PORT); Jaeger: http://127.0.0.1:16686"
