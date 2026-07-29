. (Join-Path $PSScriptRoot 'lib.ps1')

Stop-MandalaManagedProcess 'sample frontend' (Join-Path $script:MandalaRuntimeDir 'frontend.pid') 'npm run dev'
Stop-MandalaManagedProcess 'sample backend' (Join-Path $script:MandalaRuntimeDir 'backend.pid') (Join-Path $script:MandalaRepositoryRoot 'sample-app/backend/build/libs/backend-0.1.0-SNAPSHOT.jar')

& docker info *> $null
if ($LASTEXITCODE -eq 0) {
    Write-MandalaLog 'Stopping local containers (database volume is preserved).'
    Invoke-MandalaCompose down --remove-orphans
} else {
    Write-MandalaLog 'Docker daemon is unavailable; application processes were still stopped.'
}
Write-MandalaLog 'Environment stopped.'
