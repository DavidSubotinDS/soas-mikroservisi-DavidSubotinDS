param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9][a-z0-9_-]*$')]
    [string]$Username,
    [switch]$Resume
)

$ErrorActionPreference = 'Stop'
if ($Username -in @('your-dockerhub-username', 'soas-local')) {
    throw 'Use the username of your own Docker Hub account.'
}

$soasRoot = Split-Path -Parent $PSScriptRoot
$soasPreviousUsername = $env:DOCKERHUB_USERNAME
$soasServices = @(
    'naming-server', 'users-service', 'currency-exchange', 'currency-conversion',
    'bank-account', 'crypto-wallet', 'crypto-exchange', 'trade-service', 'api-gateway'
)

Push-Location -LiteralPath $soasRoot
try {
    $env:DOCKERHUB_USERNAME = $Username
    docker version --format '{{.Server.Version}}'
    if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop before publishing.' }

    if (-not $Resume) {
        # Docker reads credentials from its interactive prompt.
        docker login --username $Username
        if ($LASTEXITCODE -ne 0) { throw 'Docker Hub login failed.' }

        docker compose config --quiet
        if ($LASTEXITCODE -ne 0) { throw 'Invalid Compose configuration.' }
        docker compose build @soasServices
        if ($LASTEXITCODE -ne 0) { throw 'Image build failed.' }
    }

    $soasImages = @($soasServices | ForEach-Object { "${Username}/soas-${_}:latest" })
    docker image inspect --format '{{.Id}}' @soasImages | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'A required local image is missing. Run the script without -Resume to build the images.' }

    foreach ($soasImage in $soasImages) {
        Write-Host "Pushing $soasImage"
        docker image push --quiet $soasImage
        if ($LASTEXITCODE -ne 0) { throw "Push failed for $soasImage. Resolve the Docker error and rerun with -Resume." }
    }

    $soasLines = @(
        '# Docker Hub publication', '',
        ('Verified at ' + [DateTime]::UtcNow.ToString('u') + '.'), '',
        '| Service | Image | Registry digest |',
        '|---|---|---|'
    )
    foreach ($soasService in $soasServices) {
        $soasImage = "${Username}/soas-${soasService}:latest"
        $soasManifestJson = docker buildx imagetools inspect --format '{{json .Manifest}}' $soasImage
        if ($LASTEXITCODE -ne 0) { throw "Could not inspect the remote tag for $soasImage." }
        $soasDigest = ($soasManifestJson | ConvertFrom-Json).digest
        if ($soasDigest -notmatch '^sha256:[a-f0-9]{64}$') {
            throw "Could not verify the remote latest tag for $soasImage."
        }
        $soasUrl = "https://hub.docker.com/r/${Username}/soas-${soasService}/tags"
        $soasLines += "| $soasService | [$soasImage]($soasUrl) | $soasDigest |"
    }
    $soasReportDirectory = Join-Path $soasRoot '.runtime'
    New-Item -ItemType Directory -Path $soasReportDirectory -Force | Out-Null
    [System.IO.File]::WriteAllLines(
        (Join-Path $soasReportDirectory 'docker-hub-images.md'),
        $soasLines,
        [System.Text.UTF8Encoding]::new($false)
    )
    Write-Host 'All nine backend latest tags are published and verified. See .runtime/docker-hub-images.md.'
    Write-Host 'Set DOCKERHUB_USERNAME in .env to the same account before future Compose builds.'
} finally {
    $env:DOCKERHUB_USERNAME = $soasPreviousUsername
    Pop-Location
}
