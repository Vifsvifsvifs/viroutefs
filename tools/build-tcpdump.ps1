param(
    [string]$LibpcapArchivePath = "",
    [string]$TcpdumpArchivePath = "",
    [string]$WinFlexBisonArchivePath = "",
    [string]$AndroidNdkVersion = "27.0.12077973",
    [string]$AndroidCmakeVersion = "3.22.1"
)

$ErrorActionPreference = "Stop"
$libpcapVersion = "1.10.6"
$tcpdumpVersion = "4.99.6"
$winFlexBisonVersion = "2.5.25"
$libpcapArchiveSha256 = "ec97d1206bdd19cb6bdd043eaa9f0037aa732262ec68e070fd7c7b5f834d5dfc"
$tcpdumpArchiveSha256 = "40a8cefd45f0d2a06827e6658efb830d484868c449ad80f7efb33516af44f3da"
$winFlexBisonArchiveSha256 = "8d324b62be33604b2c45ad1dd34ab93d722534448f55a16ca7292de32b6ac135"
$tcpdumpBinarySha256 = "adb46aa539d42efb6d07c1afc42edc39954fd59a46c09561411bb98bb176c4da"
$licenseSha256 = "8a54594d257e14a5260ac770f1633516cb51e3fc28c40136ce2697014eda7afd"

function Assert-Sha256([string]$Path, [string]$Expected, [string]$Label) {
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $Expected) {
        throw "$Label SHA-256 mismatch: $actual"
    }
}

function Invoke-Checked([scriptblock]$Command, [string]$Label) {
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Label failed with exit code $LASTEXITCODE."
    }
}

function Resolve-OrDownload(
    [string]$ProvidedPath,
    [string]$Destination,
    [string]$Url,
    [string]$ExpectedSha256,
    [string]$Label
) {
    if ([string]::IsNullOrWhiteSpace($ProvidedPath)) {
        Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $Destination
        $resolved = $Destination
    } else {
        $resolved = (Resolve-Path -LiteralPath $ProvidedPath).Path
    }
    Assert-Sha256 $resolved $ExpectedSha256 $Label
    return $resolved
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$androidSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$ndkRoot = Join-Path $androidSdk "ndk\$AndroidNdkVersion"
$cmakeRoot = Join-Path $androidSdk "cmake\$AndroidCmakeVersion\bin"
$cmake = Join-Path $cmakeRoot "cmake.exe"
$ninja = Join-Path $cmakeRoot "ninja.exe"
$toolchain = Join-Path $ndkRoot "build\cmake\android.toolchain.cmake"
$ndkTools = Join-Path $ndkRoot "toolchains\llvm\prebuilt\windows-x86_64\bin"
$strip = Join-Path $ndkTools "llvm-strip.exe"
$readelf = Join-Path $ndkTools "llvm-readelf.exe"
foreach ($required in @($cmake, $ninja, $toolchain, $strip, $readelf)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required Android build tool is missing: $required"
    }
}

$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("viroutefs-tcpdump-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
try {
    $libpcapArchive = Resolve-OrDownload `
        $LibpcapArchivePath `
        (Join-Path $temporaryRoot "libpcap-$libpcapVersion.tar.xz") `
        "https://www.tcpdump.org/release/libpcap-$libpcapVersion.tar.xz" `
        $libpcapArchiveSha256 `
        "libpcap $libpcapVersion archive"
    $tcpdumpArchive = Resolve-OrDownload `
        $TcpdumpArchivePath `
        (Join-Path $temporaryRoot "tcpdump-$tcpdumpVersion.tar.xz") `
        "https://www.tcpdump.org/release/tcpdump-$tcpdumpVersion.tar.xz" `
        $tcpdumpArchiveSha256 `
        "tcpdump $tcpdumpVersion archive"
    $winFlexBisonArchive = Resolve-OrDownload `
        $WinFlexBisonArchivePath `
        (Join-Path $temporaryRoot "win_flex_bison-$winFlexBisonVersion.zip") `
        "https://github.com/lexxmark/winflexbison/releases/download/v$winFlexBisonVersion/win_flex_bison-$winFlexBisonVersion.zip" `
        $winFlexBisonArchiveSha256 `
        "winflexbison $winFlexBisonVersion archive"

    $sourceRoot = Join-Path $temporaryRoot "sources"
    New-Item -ItemType Directory -Path $sourceRoot | Out-Null
    Invoke-Checked { tar.exe -xf $libpcapArchive -C $sourceRoot } "Extracting libpcap"
    Invoke-Checked { tar.exe -xf $tcpdumpArchive -C $sourceRoot } "Extracting tcpdump"
    $winFlexRoot = Join-Path $temporaryRoot "winflexbison"
    Expand-Archive -LiteralPath $winFlexBisonArchive -DestinationPath $winFlexRoot

    $libpcapSource = Join-Path $sourceRoot "libpcap-$libpcapVersion"
    $tcpdumpSource = Join-Path $sourceRoot "tcpdump-$tcpdumpVersion"
    $libpcapCmake = Join-Path $libpcapSource "CMakeLists.txt"
    $libpcapCmakeText = Get-Content -LiteralPath $libpcapCmake -Raw
    $nullDevicePattern = 'if\(WIN32\)(\r?\n\s*set\(NULL_DEVICE "NUL:"\)\r?\nelse\(\)\r?\n\s*set\(NULL_DEVICE "/dev/null"\)\r?\nendif\(\))'
    $patchedCmakeText = [regex]::Replace(
        $libpcapCmakeText,
        $nullDevicePattern,
        'if(CMAKE_HOST_WIN32)$1',
        1
    )
    if ($patchedCmakeText -eq $libpcapCmakeText) {
        throw "Could not apply the pinned host-null-device compatibility patch to libpcap."
    }
    [IO.File]::WriteAllText($libpcapCmake, $patchedCmakeText, [Text.UTF8Encoding]::new($false))

    $libpcapBuild = Join-Path $temporaryRoot "build-libpcap"
    $winFlex = Join-Path $winFlexRoot "win_flex.exe"
    $winBison = Join-Path $winFlexRoot "win_bison.exe"
    $portableTemporaryRoot = $temporaryRoot.Replace('\', '/')
    $reproducibleCFlags = "-ffile-prefix-map=$portableTemporaryRoot=/build -fdebug-prefix-map=$portableTemporaryRoot=/build"
    Invoke-Checked {
        & $cmake -S $libpcapSource -B $libpcapBuild -G Ninja `
            "-DCMAKE_MAKE_PROGRAM=$ninja" `
            "-DCMAKE_TOOLCHAIN_FILE=$toolchain" `
            -DANDROID_ABI=arm64-v8a `
            -DANDROID_PLATFORM=android-26 `
            -DCMAKE_BUILD_TYPE=Release `
            "-DCMAKE_C_FLAGS=$reproducibleCFlags" `
            -DBUILD_SHARED_LIBS=OFF `
            -DENABLE_REMOTE=OFF `
            -DDISABLE_LINUX_USBMON=ON `
            -DDISABLE_BLUETOOTH=ON `
            -DDISABLE_NETMAP=ON `
            -DDISABLE_DBUS=ON `
            -DDISABLE_RDMA=ON `
            "-DLEX_EXECUTABLE=$winFlex" `
            "-DYACC_EXECUTABLE=$winBison"
    } "Configuring libpcap"
    Invoke-Checked { & $cmake --build $libpcapBuild --parallel 8 } "Building libpcap"

    $libpcapLibrary = Join-Path $libpcapBuild "libpcap.a"
    if (-not (Test-Path -LiteralPath $libpcapLibrary -PathType Leaf)) {
        throw "The static libpcap library was not produced."
    }
    $tcpdumpBuild = Join-Path $temporaryRoot "build-tcpdump"
    Invoke-Checked {
        & $cmake -S $tcpdumpSource -B $tcpdumpBuild -G Ninja `
            "-DCMAKE_MAKE_PROGRAM=$ninja" `
            "-DCMAKE_TOOLCHAIN_FILE=$toolchain" `
            -DANDROID_ABI=arm64-v8a `
            -DANDROID_PLATFORM=android-26 `
            -DCMAKE_BUILD_TYPE=Release `
            "-DCMAKE_C_FLAGS=$reproducibleCFlags" `
            "-DCMAKE_EXE_LINKER_FLAGS=-Wl,-z,max-page-size=16384" `
            "-DPCAP_INCLUDE_DIR=$libpcapSource" `
            "-DPCAP_LIBRARY=$libpcapLibrary" `
            -DWITH_SMI=OFF `
            -DWITH_CRYPTO=OFF `
            -DWITH_CAPSICUM=OFF `
            -DWITH_CAP_NG=OFF `
            -DENABLE_SMB=OFF
    } "Configuring tcpdump"
    Invoke-Checked { & $cmake --build $tcpdumpBuild --target tcpdump --parallel 8 } "Building tcpdump"

    $builtBinary = Join-Path $tcpdumpBuild "tcpdump"
    Invoke-Checked { & $strip --strip-all $builtBinary } "Stripping tcpdump"
    Assert-Sha256 $builtBinary $tcpdumpBinarySha256 "tcpdump Android arm64 binary"
    $programHeaders = & $readelf -lW $builtBinary
    if ($LASTEXITCODE -ne 0 -or ($programHeaders | Select-String -Pattern '^\s*LOAD\s+.*\s0x4000\s*$').Count -lt 1) {
        throw "tcpdump is not aligned for Android 16 KiB pages."
    }
    $dynamic = & $readelf -d $builtBinary
    $unexpectedDependency = $dynamic | Select-String -Pattern '\(NEEDED\)' | Where-Object {
        $_.Line -notmatch '\[(libc|libm|libdl)\.so\]'
    }
    if ($unexpectedDependency) {
        throw "tcpdump has an unexpected dynamic dependency: $($unexpectedDependency.Line)"
    }

    $license = Join-Path $tcpdumpSource "LICENSE"
    Assert-Sha256 $license $licenseSha256 "tcpdump/libpcap BSD license"
    $nativeDirectory = Join-Path $repoRoot "app\src\main\jniLibs\arm64-v8a"
    $licenseDirectory = Join-Path $repoRoot "app\src\main\assets\licenses"
    New-Item -ItemType Directory -Force -Path $nativeDirectory, $licenseDirectory | Out-Null
    Copy-Item -LiteralPath $builtBinary -Destination (Join-Path $nativeDirectory "libtcpdump.so") -Force
    Copy-Item -LiteralPath $license -Destination (Join-Path $licenseDirectory "tcpdump-libpcap-BSD.txt") -Force
    Write-Host "Installed pinned tcpdump $tcpdumpVersion + libpcap $libpcapVersion Android arm64 binary and BSD license."
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}
