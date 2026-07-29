param([switch]$Incremental)
. (Join-Path $PSScriptRoot 'lib.ps1')
Import-MandalaEnv

if (-not $env:BACKEND_PORT) { $env:BACKEND_PORT = '18080' }
if (-not $env:FRONTEND_PORT) { $env:FRONTEND_PORT = '5173' }
if (-not $env:MANDALA_CAPTURE_BASE_URL) { $env:MANDALA_CAPTURE_BASE_URL = "http://127.0.0.1:$($env:FRONTEND_PORT)" }
if (-not $env:MANDALA_CAPTURE_WEB_SERVER_URL) { $env:MANDALA_CAPTURE_WEB_SERVER_URL = "$($env:MANDALA_CAPTURE_BASE_URL)/" }
$mode = if ($Incremental) { 'INCREMENTAL' } else { 'FULL' }

Wait-MandalaHttp "http://127.0.0.1:$($env:BACKEND_PORT)/actuator/health" 'Sample backend' 5
Wait-MandalaHttp "http://127.0.0.1:$($env:FRONTEND_PORT)/" 'Sample frontend' 5
Write-MandalaLog "Running $mode Refresh (source, UI, runtime, PostgreSQL, reconciliation and rendering)."
Invoke-MandalaCli refresh --mode $mode
Invoke-MandalaCli verify
Write-MandalaLog 'Generated sample Mandala: mandala/generated/sample-app/site/index.html'
