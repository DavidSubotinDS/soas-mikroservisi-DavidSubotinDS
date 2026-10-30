$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $workspace '.runtime/processes.json'
if (-not (Test-Path -LiteralPath $pidFile)) { Write-Host 'No recorded local processes.'; exit 0 }
$records = @(Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json)
foreach ($record in $records) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $($record.Id)"
    if ($process -and $process.CommandLine -and $process.CommandLine.Contains($record.Jar)) {
        Stop-Process -Id $record.Id
        Write-Host "Stopped $($record.Service)"
    }
}
Remove-Item -LiteralPath $pidFile
