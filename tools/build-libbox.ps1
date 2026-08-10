# SPDX-License-Identifier: GPL-3.0-or-later

[CmdletBinding()]
param(
    [string]$OutputPath = "app/libs/libbox.aar",
    [string]$SourceDirectory = "build/libbox-source"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$singBoxCommit = "2dea956ea11ed9fdc47dc69fba56bea71c69ea9b"
$gomobileVersion = "v0.1.12"
$requiredGoVersion = "go1.26.5"
$requiredNdkVersion = "27.0.12077973"

if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME must point to OpenJDK 17."
}
if (-not (& (Join-Path $env:JAVA_HOME "bin/java") --version 2>&1 | Select-String "openjdk 17")) {
    throw "OpenJDK 17 is required."
}
if (-not $env:ANDROID_HOME) {
    throw "ANDROID_HOME must point to the Android SDK."
}
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

$ndkDirectory = Join-Path $env:ANDROID_HOME "ndk/$requiredNdkVersion"
if (-not (Test-Path -LiteralPath $ndkDirectory)) {
    throw "Android NDK $requiredNdkVersion is required at $ndkDirectory."
}
$env:ANDROID_NDK_HOME = $ndkDirectory
$env:ANDROID_NDK_ROOT = $ndkDirectory
# NDK r27 needs explicit ELF segment alignment for 16 KiB Android devices.
$env:CGO_LDFLAGS = "-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"

$resolvedSource = [System.IO.Path]::GetFullPath((Join-Path $PWD $SourceDirectory))
$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path $PWD $OutputPath))
if (Test-Path -LiteralPath $resolvedSource) {
    throw "Source directory already exists: $resolvedSource. Use a new -SourceDirectory for a clean reproducible build."
}

New-Item -ItemType Directory -Path (Split-Path -Parent $resolvedSource) -Force | Out-Null
git clone --filter=blob:none --no-checkout https://github.com/SagerNet/sing-box.git $resolvedSource
git -C $resolvedSource checkout --detach $singBoxCommit

$buildFile = Join-Path $resolvedSource "cmd/internal/build_libbox/main.go"
$original = [System.IO.File]::ReadAllText($buildFile)
$withNaive = ', "with_naive_outbound"'
$patched = $original.Replace($withNaive, '')
if ($patched -eq $original) {
    throw "Could not apply the pinned no-Naive build-tag change."
}
[System.IO.File]::WriteAllText($buildFile, $patched, [System.Text.UTF8Encoding]::new($false))

& $goExecutable install "github.com/sagernet/gomobile/cmd/gomobile@$gomobileVersion"
if ($LASTEXITCODE -ne 0) {
    throw "gomobile installation failed."
}

Push-Location $resolvedSource
try {
    & $goExecutable run ./cmd/internal/build_libbox -target android -platform android/arm64
    if ($LASTEXITCODE -ne 0) {
        throw "libbox build failed."
    }
} finally {
    Pop-Location
}

$builtAar = Join-Path $resolvedSource "libbox.aar"
if (-not (Test-Path -LiteralPath $builtAar)) {
    throw "The build completed without libbox.aar."
}
New-Item -ItemType Directory -Path (Split-Path -Parent $resolvedOutput) -Force | Out-Null
Copy-Item -LiteralPath $builtAar -Destination $resolvedOutput -Force
$hash = Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedOutput
Write-Output "Built 16 KiB-aligned sing-box libbox from $singBoxCommit with OpenVPN/OpenConnect and without Naive/Cronet."
Write-Output "Output: $resolvedOutput"
Write-Output "SHA-256: $($hash.Hash.ToLowerInvariant())"
