# Dot-source from any location: . .\scripts\env.ps1
$zapysRoot = Split-Path -Parent $PSScriptRoot
$zapysTools = Join-Path $zapysRoot '.tools'
$zapysJdk = Join-Path $zapysTools 'java/jdk-17.0.20.1+1'
if (-not (Test-Path -LiteralPath (Join-Path $zapysJdk 'bin/java.exe'))) {
    throw 'Project JDK is missing or incomplete. Run scripts/bootstrap.ps1.'
}
$env:JAVA_HOME = $zapysJdk
$env:ANDROID_HOME = Join-Path $zapysTools 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $zapysTools 'android-user'
$env:ANDROID_CLI_HOME = Join-Path $zapysTools 'android-cli'
$env:ANDROID_AVD_HOME = Join-Path $zapysTools 'android-avd'
$env:GRADLE_USER_HOME = Join-Path $zapysTools 'gradle-cache'
$zapysPathEntries = @(
    (Join-Path $env:ANDROID_HOME 'platform-tools'),
    (Join-Path $env:ANDROID_HOME 'emulator'),
    (Join-Path $env:ANDROID_HOME 'cmdline-tools\latest\bin'),
    (Join-Path $zapysTools 'gradle-8.11.1\bin'),
    (Join-Path $zapysTools 'gh\bin')
)
if ($env:JAVA_HOME) { $zapysPathEntries += Join-Path $env:JAVA_HOME 'bin' }
$env:PATH = ($zapysPathEntries -join ';') + ';' + $env:PATH
