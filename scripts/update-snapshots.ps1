param([switch]$Check)
. (Join-Path $PSScriptRoot 'lib.ps1')

if (-not $Check) {
    Push-Location $script:MandalaRepositoryRoot
    try {
        & (Join-Path $script:MandalaRepositoryRoot 'gradlew.bat') --console=plain ':mandala-renderer:updateRendererGolden'
        if ($LASTEXITCODE -ne 0) { throw "Renderer Golden generation failed with exit code $LASTEXITCODE." }
    } finally { Pop-Location }
}

& (Join-Path $script:MandalaRepositoryRoot 'scripts/refresh-mandala.ps1')
if ($LASTEXITCODE -ne 0) { throw "Snapshot generation failed with exit code $LASTEXITCODE." }

if ($Check) {
    & git -C $script:MandalaRepositoryRoot diff --exit-code -- mandala/generated/sample-app mandala/snapshots
    if ($LASTEXITCODE -ne 0) { throw 'Generated snapshots are stale.' }
    $untracked = & git -C $script:MandalaRepositoryRoot ls-files --others --exclude-standard -- mandala/generated/sample-app mandala/snapshots
    if ($untracked) { throw "Generated snapshots are untracked:`n$($untracked -join "`n")" }
    Write-MandalaLog 'Generated snapshots match the repository.'
} else {
    Write-MandalaLog 'Snapshots regenerated. Review git diff before committing.'
}
