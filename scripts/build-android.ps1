# Run in a normal Windows PowerShell terminal with access to Maven/Google repositories.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'env.ps1')
$zapysBuildRoot = Split-Path -Parent $PSScriptRoot
foreach ($component in @('platforms/android-35/android.jar', 'build-tools/35.0.0/aapt2.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME $component))) {
        throw "Android SDK component missing: $component. Run scripts/bootstrap.ps1 -InstallSdk -AcceptAndroidLicenses first."
    }
}
$zapysWrapper = Join-Path $zapysBuildRoot 'android/gradlew.bat'
& $zapysWrapper -p (Join-Path $zapysBuildRoot 'android') --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) { throw "Android checks/build failed with exit code $LASTEXITCODE. See the first Gradle error above." }
$zapysApk = Join-Path $zapysBuildRoot 'android/app/build/outputs/apk/debug/app-debug.apk'
if (-not (Test-Path -LiteralPath $zapysApk)) { throw "Build returned success but APK is missing: $zapysApk" }
Write-Output "Android checks/build passed. APK: $zapysApk"
