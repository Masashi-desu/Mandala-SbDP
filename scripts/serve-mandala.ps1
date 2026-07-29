param([int]$Port = 4174)
. (Join-Path $PSScriptRoot 'lib.ps1')

$index = Join-Path $script:MandalaRepositoryRoot 'mandala/generated/sample-app/site/index.html'
if (-not (Test-Path -LiteralPath $index)) { throw 'Sample Mandala is not generated. Run .\scripts\refresh-mandala.ps1 first.' }
& (Join-Path $script:MandalaRepositoryRoot 'scripts/build-site.ps1')
$publishedSample = Join-Path $script:MandalaRepositoryRoot 'site/dist/sample/index.html'
if (-not (Test-Path -LiteralPath $publishedSample)) { throw 'Published sample Mandala was not assembled under site/dist/sample.' }
Write-MandalaLog "Serving the Pages-ready bundle at http://127.0.0.1:$Port/ and the sample Mandala at http://127.0.0.1:$Port/sample/ (Ctrl+C to stop)."
Invoke-MandalaCli serve --root site/dist --bind 127.0.0.1 --port $Port
