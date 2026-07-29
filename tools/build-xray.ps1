# SPDX-License-Identifier: GPL-3.0-or-later

[CmdletBinding()]
param(
    [string]$OutputPath = "app/src/main/jniLibs/arm64-v8a/libxray.so",
    [string]$SourceDirectory = "build/xray-core-source"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$xrayCommit = "94ffd50060f1cfd5d7482ec90a23a92bdefdff68"
$requiredGoVersion = "go1.26.5"
$expectedSha256 = "9bb0b815086395164066b5fa27b1797bf9a0fcc493d1491f02166560604dcaff"

if (-not $env:GOROOT) {
    throw "GOROOT must point to Go $requiredGoVersion."
}
$goExecutable = Join-Path $env:GOROOT "bin/go.exe"
if (-not (Test-Path -LiteralPath $goExecutable)) {
    throw "Go executable not found at $goExecutable."
}
if ((& $goExecutable version) -notmatch [regex]::Escape($requiredGoVersion)) {
    throw "Go $requiredGoVersion is required."
}

$resolvedSource = [System.IO.Path]::GetFullPath((Join-Path $PWD $SourceDirectory))
$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path $PWD $OutputPath))
if (Test-Path -LiteralPath $resolvedSource) {
    throw "Source directory already exists: $resolvedSource. Use a new -SourceDirectory for a clean reproducible build."
}

New-Item -ItemType Directory -Path (Split-Path -Parent $resolvedSource) -Force | Out-Null
git clone --filter=blob:none --no-checkout https://github.com/XTLS/Xray-core.git $resolvedSource
if ($LASTEXITCODE -ne 0) {
    throw "Xray-core clone failed."
}
# The release artifact is byte-reproducible on Windows only when Git does not
# rewrite upstream LF source files to CRLF during checkout.
git -C $resolvedSource config core.autocrlf false
git -C $resolvedSource checkout --detach $xrayCommit
if ($LASTEXITCODE -ne 0) {
    throw "Could not check out pinned Xray-core commit $xrayCommit."
}

$previousGoOs = $env:GOOS
$previousGoArch = $env:GOARCH
$previousCgo = $env:CGO_ENABLED
$env:GOOS = "android"
$env:GOARCH = "arm64"
$env:CGO_ENABLED = "0"
try {
    New-Item -ItemType Directory -Path (Split-Path -Parent $resolvedOutput) -Force | Out-Null
    Push-Location $resolvedSource
    try {
        & $goExecutable build `
            -buildvcs=false `
            -trimpath `
            "-ldflags=-s -w -buildid= -checklinkname=0" `
            -o $resolvedOutput `
            ./main
        if ($LASTEXITCODE -ne 0) {
            throw "Xray-core Android build failed."
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:GOOS = $previousGoOs
    $env:GOARCH = $previousGoArch
    $env:CGO_ENABLED = $previousCgo
}

$actualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedOutput).Hash.ToLowerInvariant()
if ($actualSha256 -ne $expectedSha256) {
    throw "Unexpected Xray-core output SHA-256: $actualSha256"
}
Write-Output "Built pinned Xray-core from $xrayCommit as an Android arm64 app-private executable."
Write-Output "Output: $resolvedOutput"
Write-Output "SHA-256: $actualSha256"
