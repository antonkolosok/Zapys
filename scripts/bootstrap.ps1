param([switch]$InstallSdk, [switch]$AcceptAndroidLicenses)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$zapysRoot = Split-Path -Parent $PSScriptRoot
$zapysTools = Join-Path $zapysRoot '.tools'
New-Item -ItemType Directory -Path $zapysTools -Force | Out-Null

function Get-VerifiedArchive([string]$Uri, [string]$Name, [string]$Sha256) {
    $destination = Join-Path $zapysTools $Name
    if (-not (Test-Path -LiteralPath $destination)) {
        Invoke-WebRequest -Uri $Uri -OutFile $destination -UseBasicParsing -TimeoutSec 300
    }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $destination).Hash.ToLowerInvariant() -ne $Sha256.ToLowerInvariant()) {
        throw "Checksum mismatch for $Name; inspect/remove that archive before retrying."
    }
    return $destination
}

if (-not (Test-Path -LiteralPath (Join-Path $zapysTools 'java/jdk-17.0.20.1+1/bin/java.exe'))) {
    $archive = Get-VerifiedArchive 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip' 'jdk-17.0.20.1_1.zip' 'e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0'
    Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $zapysTools 'java') -Force
}
if (-not (Test-Path -LiteralPath (Join-Path $zapysTools 'gradle-8.11.1\bin\gradle.bat'))) {
    $archive = Get-VerifiedArchive 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' 'gradle.zip' 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6'
    Expand-Archive -LiteralPath $archive -DestinationPath $zapysTools -Force
}
if (-not (Test-Path -LiteralPath (Join-Path $zapysTools 'gh\bin\gh.exe'))) {
    $archive = Get-VerifiedArchive 'https://github.com/cli/cli/releases/download/v2.103.3/gh_2.103.3_windows_amd64.zip' 'gh.zip' '98d0cfa794721665d7973dd728c78aff7bd836d58f10f6d7a03568eccb0a22e0'
    Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $zapysTools 'gh') -Force
}
if (-not (Test-Path -LiteralPath (Join-Path $zapysTools 'android-sdk\cmdline-tools\latest\bin\android.exe'))) {
    $archive = Get-VerifiedArchive 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' 'android-commandline.zip' '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
    $extract = Join-Path $zapysTools 'android-cli-extract'
    Expand-Archive -LiteralPath $archive -DestinationPath $extract -Force
    $target = Join-Path $zapysTools 'android-sdk\cmdline-tools\latest'
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $extract 'cmdline-tools') | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $target -Recurse -Force }
}
. (Join-Path $PSScriptRoot 'env.ps1')
& (Join-Path $env:JAVA_HOME 'bin/java.exe') -version
if ($LASTEXITCODE -ne 0) { throw 'JDK verification failed' }
& (Join-Path $zapysTools 'gradle-8.11.1/bin/gradle.bat') --version
if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed' }
& (Join-Path $zapysTools 'gh/bin/gh.exe') --version
if ($LASTEXITCODE -ne 0) { throw 'GitHub CLI verification failed' }
if ($InstallSdk) {
    # Command-line tools 22 replaced sdkmanager with Android CLI. Keep its
    # state inside this project via env.ps1, and do not treat the deprecated
    # sdkmanager's successful no-op exit as an installed SDK.
    if ($AcceptAndroidLicenses) {
        1..50 | ForEach-Object { 'y' } | & android --no-metrics "--sdk=$env:ANDROID_HOME" sdk install 'platform-tools' 'platforms/android-35' 'build-tools/35.0.0'
    } else {
        & android --no-metrics "--sdk=$env:ANDROID_HOME" sdk install 'platform-tools' 'platforms/android-35' 'build-tools/35.0.0'
    }
    if ($LASTEXITCODE -ne 0) { throw 'Android SDK setup failed' }
    foreach ($required in @('platforms/android-35/android.jar', 'build-tools/35.0.0/aapt2.exe', 'platform-tools/adb.exe')) {
        if (-not (Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME $required))) { throw "SDK component missing: $required" }
    }
    Write-Output 'Android SDK 35 installed and component files verified.'
}
Write-Output 'Project Java, Gradle and gh launch verified. Android CLI launcher file present; its runtime and SDK require -InstallSdk and network access. iOS builds require macOS/Xcode.'
