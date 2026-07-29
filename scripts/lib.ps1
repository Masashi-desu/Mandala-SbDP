Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:MandalaRepositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$script:MandalaRuntimeDir = Join-Path $script:MandalaRepositoryRoot '.runtime'
$script:MandalaToolDir = Join-Path $script:MandalaRepositoryRoot '.tools'
$script:MandalaLocalInfraDir = Join-Path $script:MandalaRepositoryRoot 'infra/local'
$script:MandalaComposeFile = Join-Path $script:MandalaLocalInfraDir 'compose.yaml'

function Write-MandalaLog {
    param([Parameter(Mandatory)][string]$Message)
    Write-Host "[mandala] $Message"
}

function Assert-MandalaCommand {
    param([Parameter(Mandatory)][string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command is not available: $Name"
    }
}

function Import-MandalaEnv {
    $envFile = Join-Path $script:MandalaRepositoryRoot '.env'
    if (-not (Test-Path -LiteralPath $envFile)) {
        throw '.env is missing. Run .\scripts\setup.ps1 first.'
    }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) { throw "Invalid line in .env: $line" }
        $key = $trimmed.Substring(0, $separator).Trim()
        if ($key -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { throw "Invalid key in .env: $key" }
        $value = $trimmed.Substring($separator + 1).Trim().Trim('"').Trim("'")
        if ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($key, 'Process'))) {
            [Environment]::SetEnvironmentVariable($key, $value, 'Process')
        }
    }
}

function Test-DockerComposePlugin {
    & docker compose version *> $null
    return $LASTEXITCODE -eq 0
}

function Invoke-MandalaCompose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $base = @('--project-directory', $script:MandalaRepositoryRoot, '--file', $script:MandalaComposeFile)
    if (Test-DockerComposePlugin) {
        & docker compose @base @Arguments
    } else {
        $standalone = Join-Path $script:MandalaToolDir 'docker-compose.exe'
        if (-not (Test-Path -LiteralPath $standalone)) {
            throw 'Docker Compose is unavailable. Run .\scripts\setup.ps1 first.'
        }
        & $standalone @base @Arguments
    }
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed with exit code $LASTEXITCODE." }
}

function Wait-MandalaHttp {
    param(
        [Parameter(Mandatory)][string]$Url,
        [Parameter(Mandatory)][string]$Label,
        [int]$Attempts = 90
    )
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri $Url -Method Get -TimeoutSec 2 -UseBasicParsing
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
                Write-MandalaLog "$Label is ready ($Url)"
                return
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    throw "$Label did not become ready: $Url"
}

function Get-MandalaProcessIdentity {
    param([Parameter(Mandatory)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return $false }
    $raw = (Get-Content -LiteralPath $Path -Raw).Trim()
    if ($raw -match '^\d+$') { return [pscustomobject]@{ pid = [int]$raw; started = ''; marker = '' } }
    try {
        $values = @{}
        foreach ($line in ($raw -split "`r?`n")) {
            $separator = $line.IndexOf('=')
            if ($separator -gt 0) { $values[$line.Substring(0, $separator)] = $line.Substring($separator + 1) }
        }
        if ($values.pid -notmatch '^\d+$') { return $false }
        return [pscustomobject]@{ pid = [int]$values.pid; started = [string]$values.started; marker = [string]$values.marker }
    } catch { return $false }
}

function Test-MandalaPidFile {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$ExpectedMarker)
    $identity = Get-MandalaProcessIdentity $Path
    if (-not $identity) { return $false }
    $process = Get-Process -Id $identity.pid -ErrorAction SilentlyContinue
    if (-not $process) { return $false }
    $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($identity.pid)" -ErrorAction SilentlyContinue
    if (-not $details -or $details.CommandLine -notlike "*$ExpectedMarker*") { return $false }
    if ($identity.started -or $identity.marker) {
        if ($identity.marker -ne $ExpectedMarker) { return $false }
        if ($process.StartTime.ToUniversalTime().ToString('o') -ne $identity.started) { return $false }
    }
    return $true
}

function Set-MandalaPidFile {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)]$Process, [Parameter(Mandatory)][string]$Marker)
    @(
        "pid=$($Process.Id)"
        "started=$($Process.StartTime.ToUniversalTime().ToString('o'))"
        "marker=$Marker"
    ) | Set-Content -LiteralPath $Path
}

function Stop-MandalaManagedProcess {
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$ExpectedMarker)
    if (-not (Test-Path -LiteralPath $Path)) { return }
    $identity = Get-MandalaProcessIdentity $Path
    $processId = if ($identity) { $identity.pid } else { 0 }
    if ($processId -and (Test-MandalaPidFile $Path $ExpectedMarker)) {
        Write-MandalaLog "Stopping $Name (PID $processId)."
        & taskkill.exe /PID $processId /T /F *> $null
    } elseif ($processId -and (Get-Process -Id $processId -ErrorAction SilentlyContinue)) {
        Write-MandalaLog "Refusing to stop ${Name}: PID $processId no longer matches the recorded Mandala process identity."
    }
    Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
}

function Invoke-MandalaCli {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $joined = $Arguments -join ' '
    Push-Location $script:MandalaRepositoryRoot
    try {
        & (Join-Path $script:MandalaRepositoryRoot 'gradlew.bat') --console=plain :mandala-cli:run "--args=$joined"
        if ($LASTEXITCODE -ne 0) { throw "Mandala CLI failed with exit code $LASTEXITCODE." }
    } finally {
        Pop-Location
    }
}

function Install-MandalaNpmDependencies {
    Push-Location $script:MandalaRepositoryRoot
    try {
        if (Test-Path -LiteralPath (Join-Path $script:MandalaRepositoryRoot 'package-lock.json')) {
            & npm.cmd ci
        } else {
            & npm.cmd install
        }
        if ($LASTEXITCODE -ne 0) { throw "npm dependency installation failed with exit code $LASTEXITCODE." }
    } finally {
        Pop-Location
    }
}
