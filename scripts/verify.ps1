. (Join-Path $PSScriptRoot 'lib.ps1')
Import-MandalaEnv

Assert-MandalaCommand 'java'
Assert-MandalaCommand 'node'
Assert-MandalaCommand 'npm.cmd'
Assert-MandalaCommand 'docker'

if (-not $env:BACKEND_PORT) { $env:BACKEND_PORT = '18080' }
if (-not $env:FRONTEND_PORT) { $env:FRONTEND_PORT = '5173' }
$env:MANDALA_REPOSITORY_ROOT = $script:MandalaRepositoryRoot
$startedBackend = $false
$startedFrontend = $false
try {
    Write-MandalaLog 'Verifying repository responsibility boundaries.'
    @(
        'platform/README.md',
        'platform/java/mandala-model',
        'platform/java/mandala-core',
        'platform/java/mandala-cli',
        'platform/playwright-capture',
        'platform/agent-skills',
        'mandala/README.md',
        'mandala/config/mandala.yml',
        'infra/local/compose.yaml',
        'site/package.json'
    ) | ForEach-Object {
        if (-not (Test-Path -LiteralPath (Join-Path $script:MandalaRepositoryRoot $_))) {
            throw "Required responsibility boundary is missing: $_"
        }
    }
    @('mandala-core', 'mandala-cli', 'tools', 'docker', 'docker-compose.yml', 'output') | ForEach-Object {
        if (Test-Path -LiteralPath (Join-Path $script:MandalaRepositoryRoot $_)) {
            throw "Legacy root path must be migrated: $_"
        }
    }

    Write-MandalaLog 'Running Java unit, integration and Golden tests.'
    Push-Location $script:MandalaRepositoryRoot
    try {
        & (Join-Path $script:MandalaRepositoryRoot 'gradlew.bat') --console=plain check
        if ($LASTEXITCODE -ne 0) { throw "Java verification failed with exit code $LASTEXITCODE." }
    } finally { Pop-Location }

    Write-MandalaLog 'Running TypeScript unit tests, type checks and production builds.'
    Install-MandalaNpmDependencies
    Push-Location $script:MandalaRepositoryRoot
    try {
        & npm.cmd test
        if ($LASTEXITCODE -ne 0) { throw "TypeScript tests failed with exit code $LASTEXITCODE." }
        & npm.cmd run typecheck
        if ($LASTEXITCODE -ne 0) { throw "TypeScript type check failed with exit code $LASTEXITCODE." }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw "TypeScript build failed with exit code $LASTEXITCODE." }
    } finally { Pop-Location }

    $backendReady = $false
    $frontendReady = $false
    $backendPidFile = Join-Path $script:MandalaRuntimeDir 'backend.pid'
    $backendJar = Join-Path $script:MandalaRepositoryRoot 'sample-app/backend/build/libs/backend-0.1.0-SNAPSHOT.jar'
    $frontendPidFile = Join-Path $script:MandalaRuntimeDir 'frontend.pid'
    try {
        Invoke-WebRequest -Uri "http://127.0.0.1:$($env:BACKEND_PORT)/actuator/health" -TimeoutSec 2 -UseBasicParsing | Out-Null
        if (-not (Test-MandalaPidFile $backendPidFile $backendJar)) {
            throw 'Backend endpoint is served by an unmanaged process. Change BACKEND_PORT in .env.'
        }
        $backendReady = $true
    } catch {
        if ($_.Exception.Message -like 'Backend endpoint is served by an unmanaged process*') { throw }
    }
    try {
        Invoke-WebRequest -Uri "http://127.0.0.1:$($env:FRONTEND_PORT)/" -TimeoutSec 2 -UseBasicParsing | Out-Null
        if (-not (Test-MandalaPidFile $frontendPidFile 'npm run dev')) {
            throw 'Frontend endpoint is served by an unmanaged process. Change FRONTEND_PORT in .env.'
        }
        $frontendReady = $true
    } catch {
        if ($_.Exception.Message -like 'Frontend endpoint is served by an unmanaged process*') { throw }
    }
    if (-not $backendReady -or -not $frontendReady) {
        $startedBackend = -not $backendReady
        $startedFrontend = -not $frontendReady
        & (Join-Path $script:MandalaRepositoryRoot 'scripts/start.ps1')
    }

    Write-MandalaLog 'Running real PostgreSQL catalog and sample compatibility integration tests.'
    $env:MANDALA_TEST_POSTGRES_URL = $env:MANDALA_DB_URL
    $env:MANDALA_TEST_POSTGRES_USER = $env:MANDALA_DB_USERNAME
    $env:MANDALA_TEST_POSTGRES_PASSWORD = $env:MANDALA_DB_PASSWORD
    Push-Location $script:MandalaRepositoryRoot
    try {
        & (Join-Path $script:MandalaRepositoryRoot 'gradlew.bat') --console=plain `
            ':mandala-postgres:test' '--tests' 'io.github.mandala.sbdp.postgres.PostgresIntegrationTest' `
            ':mandala-spring:test' '--tests' 'io.github.mandala.sbdp.spring.SampleSpringCompatibilityTest' `
            ':mandala-doma:test' '--tests' 'io.github.mandala.sbdp.doma.SampleDomaCompatibilityTest' `
            '--rerun-tasks'
        if ($LASTEXITCODE -ne 0) { throw "Integration verification failed with exit code $LASTEXITCODE." }
    } finally { Pop-Location }

    Write-MandalaLog 'Running deterministic Playwright capture and a real full-stack Mandala Refresh.'
    & (Join-Path $script:MandalaRepositoryRoot 'scripts/refresh-mandala.ps1')
    Invoke-MandalaCli verify

    $graph = Join-Path $script:MandalaRepositoryRoot 'mandala/generated/sample-app/graph/mandala.json'
    $sampleIndex = Join-Path $script:MandalaRepositoryRoot 'mandala/generated/sample-app/site/index.html'
    if (-not (Test-Path -LiteralPath $graph) -or (Get-Item -LiteralPath $graph).Length -eq 0) { throw 'Documentation Graph was not generated.' }
    if (-not (Test-Path -LiteralPath $sampleIndex)) { throw 'Sample Mandala site was not generated.' }

    & (Join-Path $script:MandalaRepositoryRoot 'scripts/build-site.ps1')
    Write-MandalaLog 'Verification passed.'
} finally {
    if ($startedBackend -and $startedFrontend) {
        & (Join-Path $script:MandalaRepositoryRoot 'scripts/stop.ps1')
    } else {
        if ($startedFrontend) { Stop-MandalaManagedProcess 'sample frontend' (Join-Path $script:MandalaRuntimeDir 'frontend.pid') 'npm run dev' }
        if ($startedBackend) { Stop-MandalaManagedProcess 'sample backend' (Join-Path $script:MandalaRuntimeDir 'backend.pid') (Join-Path $script:MandalaRepositoryRoot 'sample-app/backend/build/libs/backend-0.1.0-SNAPSHOT.jar') }
    }
}
