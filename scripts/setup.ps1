. (Join-Path $PSScriptRoot 'lib.ps1')

$composeVersion = '2.39.1'
$otelAgentVersion = '2.16.0'
$otelAgentSha256 = '1b0246d3e60b608b07836a9656e1a97bb7d084b088111ef34ecd47483acebcf5'

Assert-MandalaCommand 'java'
Assert-MandalaCommand 'node'
Assert-MandalaCommand 'npm.cmd'
Assert-MandalaCommand 'docker'
Assert-MandalaCommand 'git'

$javaVersion = (& java -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "21(?:\.|\")') { throw "Java 21 is required. Detected: $javaVersion" }
$nodeMajor = [int]((& node -p 'process.versions.node.split(".")[0]').Trim())
if ($nodeMajor -lt 24) { throw "Node.js 24 or newer is required. Detected: $(& node --version)" }
& docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker daemon is not running.' }
if (-not (Test-Path -LiteralPath (Join-Path $script:MandalaRepositoryRoot 'gradlew.bat'))) { throw 'Gradle Wrapper is missing.' }

$directories = @(
    $script:MandalaToolDir,
    (Join-Path $script:MandalaRuntimeDir 'logs'),
    (Join-Path $script:MandalaRepositoryRoot 'mandala/cache'),
    (Join-Path $script:MandalaRepositoryRoot 'mandala/snapshots/db'),
    (Join-Path $script:MandalaRepositoryRoot 'mandala/snapshots/runtime'),
    (Join-Path $script:MandalaRepositoryRoot 'mandala/snapshots/ui'),
    (Join-Path $script:MandalaRepositoryRoot 'mandala/traces/runtime'),
    (Join-Path $script:MandalaRepositoryRoot 'mandala/generated/sample-app')
)
foreach ($directory in $directories) { New-Item -ItemType Directory -Path $directory -Force | Out-Null }

$envPath = Join-Path $script:MandalaRepositoryRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) {
    Copy-Item -LiteralPath (Join-Path $script:MandalaRepositoryRoot '.env.example') -Destination $envPath
    Write-MandalaLog 'Created .env from .env.example (local development values only).'
} else {
    Write-MandalaLog 'Keeping existing .env.'
}
Import-MandalaEnv

if (-not (Test-DockerComposePlugin)) {
    $composePath = Join-Path $script:MandalaToolDir 'docker-compose.exe'
    $architecture = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'aarch64' } else { 'x86_64' }
    $composeAsset = "docker-compose-windows-$architecture.exe"
    if (-not (Test-Path -LiteralPath $composePath)) {
        $composeUrl = "https://github.com/docker/compose/releases/download/v$composeVersion/$composeAsset"
        Write-MandalaLog "Downloading pinned Docker Compose v$composeVersion."
        Invoke-WebRequest -Uri $composeUrl -OutFile $composePath
    }
    $checksumsPath = Join-Path $script:MandalaToolDir "docker-compose-$composeVersion-checksums.txt"
    Invoke-WebRequest -Uri "https://github.com/docker/compose/releases/download/v$composeVersion/checksums.txt" -OutFile $checksumsPath
    $checksumLine = Get-Content -LiteralPath $checksumsPath | Where-Object { $_ -match "\*$([regex]::Escape($composeAsset))$" } | Select-Object -First 1
    if (-not $checksumLine) { throw "Checksum for $composeAsset was not found." }
    $expectedComposeSha = ($checksumLine -split '\s+')[0].ToLowerInvariant()
    $actualComposeSha = (Get-FileHash -LiteralPath $composePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualComposeSha -ne $expectedComposeSha) { throw 'Docker Compose checksum mismatch.' }
}

$agentPath = Join-Path $script:MandalaToolDir "opentelemetry-javaagent-$otelAgentVersion.jar"
if (-not (Test-Path -LiteralPath $agentPath)) {
    $agentUrl = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v$otelAgentVersion/opentelemetry-javaagent.jar"
    Write-MandalaLog "Downloading pinned OpenTelemetry Java agent $otelAgentVersion."
    Invoke-WebRequest -Uri $agentUrl -OutFile $agentPath
}
$actualAgentSha = (Get-FileHash -LiteralPath $agentPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualAgentSha -ne $otelAgentSha256) { throw 'OpenTelemetry Java agent checksum mismatch.' }

Write-MandalaLog 'Resolving Gradle dependencies.'
Push-Location $script:MandalaRepositoryRoot
try {
    & (Join-Path $script:MandalaRepositoryRoot 'gradlew.bat') --console=plain classes testClasses -x test
    if ($LASTEXITCODE -ne 0) { throw "Gradle dependency resolution failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }

Write-MandalaLog 'Installing pinned npm workspace dependencies.'
Install-MandalaNpmDependencies

Write-MandalaLog 'Installing Playwright Chromium.'
Push-Location $script:MandalaRepositoryRoot
try {
    & npm.cmd exec --workspace '@mandala/playwright-capture' playwright install chromium
    if ($LASTEXITCODE -ne 0) { throw "Playwright installation failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }

Invoke-MandalaCompose config --quiet
Write-MandalaLog 'Pulling pinned PostgreSQL, OpenTelemetry Collector, Jaeger and helper images.'
Invoke-MandalaCompose pull postgres jaeger
Invoke-MandalaCompose build --pull otel-collector
& docker pull alpine:3.22.0 *> $null
if ($LASTEXITCODE -ne 0) { throw 'Failed to pull the pinned Alpine helper image.' }
Write-MandalaLog 'Setup complete. Next: .\scripts\start.ps1'
