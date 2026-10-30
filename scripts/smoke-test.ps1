param([string]$BaseUrl = 'http://localhost:8765')
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
& node scripts/smoke-test.mjs "--base=$BaseUrl"
exit $LASTEXITCODE
