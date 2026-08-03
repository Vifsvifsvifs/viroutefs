param(
    [string]$ArchivePath = ""
)

$ErrorActionPreference = "Stop"
$release = "v1.0.4"
$archiveSha256 = "5760b6d41c09459fff00b4a6fec5437a471a00aac15f734723ede149cd26c709"
$expected = @{
    "binaries/android-arm64/nfqws2" = "2e1a0e950e0bc7189b5662e54fdd66d749d51215b167a647f15659554e7b4090"
    "lua/zapret-lib.lua" = "b272d207cca145a3b6174793b7d335489519f6d4299418ff2b870765cea24d5a"
    "lua/zapret-antidpi.lua" = "31c9dd75b0bd55e98e5306293f2be81e9d2ecadcbbf9157394ff37dcff7dc85a"
    "lua/zapret-auto.lua" = "aacfde0c95c3058f8e95f5d7d244398bdc03ebf846a8f17322129fb543366a3d"
    "docs/LICENSE.txt" = "d089978dd77d53cb6aa5dff51cfdbff617e52dd44af1ac44a6df02c6644f17d5"
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("viroutefs-zapret2-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
try {
    if ([string]::IsNullOrWhiteSpace($ArchivePath)) {
        $ArchivePath = Join-Path $temporaryRoot "zapret2-$release.zip"
        $url = "https://github.com/bol-van/zapret2/releases/download/$release/zapret2-$release.zip"
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $ArchivePath
    } else {
        $ArchivePath = (Resolve-Path -LiteralPath $ArchivePath).Path
    }
    $actualArchiveHash = (Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualArchiveHash -ne $archiveSha256) {
        throw "zapret2 release archive SHA-256 mismatch: $actualArchiveHash"
    }

    $extractRoot = Join-Path $temporaryRoot "extract"
    Expand-Archive -LiteralPath $ArchivePath -DestinationPath $extractRoot
    $sourceRoot = Join-Path $extractRoot "zapret2-$release"
    foreach ($entry in $expected.GetEnumerator()) {
        $source = Join-Path $sourceRoot ($entry.Key -replace "/", [IO.Path]::DirectorySeparatorChar)
        $actualHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne $entry.Value) {
            throw "zapret2 file SHA-256 mismatch for $($entry.Key): $actualHash"
        }
    }

    $nativeDirectory = Join-Path $repoRoot "app/src/main/jniLibs/arm64-v8a"
    $assetDirectory = Join-Path $repoRoot "app/src/main/assets/zapret2"
    $licenseDirectory = Join-Path $repoRoot "app/src/main/assets/licenses"
    New-Item -ItemType Directory -Force -Path $nativeDirectory, $assetDirectory, $licenseDirectory | Out-Null
    Copy-Item -LiteralPath (Join-Path $sourceRoot "binaries/android-arm64/nfqws2") -Destination (Join-Path $nativeDirectory "libzapret2.so") -Force
    Copy-Item -LiteralPath (Join-Path $sourceRoot "lua/zapret-lib.lua") -Destination (Join-Path $assetDirectory "zapret-lib.lua") -Force
    Copy-Item -LiteralPath (Join-Path $sourceRoot "lua/zapret-antidpi.lua") -Destination (Join-Path $assetDirectory "zapret-antidpi.lua") -Force
    Copy-Item -LiteralPath (Join-Path $sourceRoot "lua/zapret-auto.lua") -Destination (Join-Path $assetDirectory "zapret-auto.lua") -Force
    Copy-Item -LiteralPath (Join-Path $sourceRoot "docs/LICENSE.txt") -Destination (Join-Path $licenseDirectory "zapret2-MIT.txt") -Force
    Write-Host "Installed pinned zapret2 $release Android arm64 binary, Lua runtime, and MIT license."
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}
