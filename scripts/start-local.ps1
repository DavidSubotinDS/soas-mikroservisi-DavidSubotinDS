param([switch]$Build)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $workspace
if ($Build) { & mvn.cmd clean package; if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' } }
$javaExecutable = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java).Source }
$runtimeDirectory = Join-Path $workspace '.runtime'
New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
$pidFile = Join-Path $runtimeDirectory 'processes.json'
if (Test-Path -LiteralPath $pidFile) { throw 'A local run is already recorded. Run scripts/stop-local.ps1 first.' }
$services = @('naming-server','users-service','bank-account','crypto-wallet','currency-exchange','crypto-exchange','currency-conversion','trade-service','api-gateway')
$processes = @()
try {
    foreach ($service in $services) {
        $jar = Join-Path $workspace "$service/target/$service-1.0.0.jar"
        if (-not (Test-Path -LiteralPath $jar)) { throw "Missing $jar. Run mvn clean package." }
        $process = Start-Process -FilePath $javaExecutable -ArgumentList @('-Xms48m','-Xmx256m','-jar',"`"$jar`"") -WindowStyle Hidden -WorkingDirectory $workspace -RedirectStandardOutput (Join-Path $runtimeDirectory "$service.log") -RedirectStandardError (Join-Path $runtimeDirectory "$service.err.log") -PassThru
        $processes += [pscustomobject]@{ Service = $service; Id = $process.Id; Executable = $javaExecutable; Jar = $jar }
        $processes | ConvertTo-Json | Set-Content -LiteralPath $pidFile
        Write-Host "Started $service (PID $($process.Id))"
        if ($service -eq 'naming-server') { Start-Sleep -Seconds 8 }
    }
    Write-Host 'Allow up to 90 seconds for discovery and demo provisioning. Gateway: http://localhost:8765; Eureka: http://localhost:8761'
} catch {
    Write-Host 'Startup failed. Recorded processes can be stopped with scripts/stop-local.ps1.'
    throw
}
