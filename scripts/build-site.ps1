. (Join-Path $PSScriptRoot 'lib.ps1')

if (-not (Test-Path -LiteralPath (Join-Path $script:MandalaRepositoryRoot 'node_modules'))) {
    Install-MandalaNpmDependencies
}
Push-Location $script:MandalaRepositoryRoot
try {
    & npm.cmd run site:build
    if ($LASTEXITCODE -ne 0) { throw "Official site build failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }

$dist = Join-Path $script:MandalaRepositoryRoot 'site/dist'
if (-not (Test-Path -LiteralPath (Join-Path $dist 'index.html'))) { throw 'Official site build did not produce site/dist/index.html.' }
if (-not (Test-Path -LiteralPath (Join-Path $dist 'en/index.html'))) { throw 'Official site build did not produce site/dist/en/index.html.' }
if (-not (Test-Path -LiteralPath (Join-Path $dist 'docs/overview.html'))) { throw 'Official site build did not produce site/dist/docs/overview.html.' }
if (-not (Test-Path -LiteralPath (Join-Path $dist 'docs/en/overview.html'))) { throw 'Official site build did not produce site/dist/docs/en/overview.html.' }
if (-not (Test-Path -LiteralPath (Join-Path $dist 'sample/index.html'))) { throw 'Published bundle did not produce site/dist/sample/index.html.' }
$forbidden = Get-ChildItem -LiteralPath $dist -File -Recurse | Where-Object {
    $_.Name -in @('mandala.json', 'otlp.json', 'mandala.yml', '.env')
}
if ($forbidden) { throw 'The Pages-ready bundle contains a raw graph, trace or local configuration.' }
Write-MandalaLog 'Pages-ready bundle built: landing pages at / and /en/, documentation under docs/ and docs/en/, and the sample Mandala under sample/.'
