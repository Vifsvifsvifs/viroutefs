# SPDX-License-Identifier: GPL-3.0-or-later

[CmdletBinding()]
param(
    [string]$OutputPath = "app/src/main/jniLibs/arm64-v8a/libbyedpi.so",
    [string]$SourceDirectory = "build/byedpi-source"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$byeDpiCommit = "ba532298de7b28cfe854aea83d061369d13ca290"
$requiredNdkVersion = "27.0.12077973"
$expectedSha256 = "abae93da6e426da5bbe5611f53a550eccb021d7be88b2c13865461024c4862d1"

if (-not $env:ANDROID_HOME) {
    throw "ANDROID_HOME must point to the Android SDK."
}
$ndkDirectory = Join-Path $env:ANDROID_HOME "ndk/$requiredNdkVersion"
$compiler = Join-Path $ndkDirectory "toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android26-clang.cmd"
if (-not (Test-Path -LiteralPath $compiler)) {
    throw "Android NDK $requiredNdkVersion compiler was not found at $compiler."
}

$resolvedSource = [System.IO.Path]::GetFullPath((Join-Path $PWD $SourceDirectory))
$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path $PWD $OutputPath))
if (Test-Path -LiteralPath $resolvedSource) {
    throw "Source directory already exists: $resolvedSource. Use a new -SourceDirectory for a clean reproducible build."
}

New-Item -ItemType Directory -Path (Split-Path -Parent $resolvedSource) -Force | Out-Null
git clone --filter=blob:none --no-checkout https://github.com/hufrea/byedpi.git $resolvedSource
git -C $resolvedSource checkout --detach $byeDpiCommit

$sources = @(
    "packets.c",
    "main.c",
    "conev.c",
    "proxy.c",
    "desync.c",
    "mpool.c",
    "extend.c"
) | ForEach-Object { Join-Path $resolvedSource $_ }

New-Item -ItemType Directory -Path (Split-Path -Parent $resolvedOutput) -Force | Out-Null
& $compiler `
    -D_DEFAULT_SOURCE `
    -DANDROID_APP `
    "-I$resolvedSource" `
    "-ffile-prefix-map=$resolvedSource=byedpi" `
    "-fmacro-prefix-map=$resolvedSource=byedpi" `
    -std=c99 `
    -O2 `
    -Wall `
    -Wno-unused `
    -Wextra `
    -Wno-unused-parameter `
    -pedantic `
    @sources `
    -llog `
    "-Wl,-z,max-page-size=16384" `
    "-Wl,-z,common-page-size=16384" `
    -o $resolvedOutput
if ($LASTEXITCODE -ne 0) {
    throw "ByeDPI Android build failed."
}

$actualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedOutput).Hash.ToLowerInvariant()
if ($actualSha256 -ne $expectedSha256) {
    throw "Unexpected ByeDPI output SHA-256: $actualSha256"
}
Write-Output "Built 16 KiB-aligned ByeDPI from $byeDpiCommit for Android arm64-v8a."
Write-Output "Output: $resolvedOutput"
Write-Output "SHA-256: $actualSha256"
