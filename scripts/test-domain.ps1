# Offline fallback only. Uses the Kotlin compiler and JUnit bundled in Gradle;
# this is NOT an Android build, lint, instrumentation or the pinned Kotlin 2.1.20 gate.
param([string[]]$Tests)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'env.ps1')
$zapysRoot = Split-Path -Parent $PSScriptRoot
$zapysLib = Join-Path $zapysRoot '.tools/gradle-8.11.1/lib'
$zapysClasses = Join-Path $zapysRoot ('artifacts/domain-tests/' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $zapysClasses -Force | Out-Null
$zapysCompileClasspath = @('kotlin-stdlib-2.0.20.jar','junit-4.13.2.jar','hamcrest-core-1.3.jar') | ForEach-Object { Join-Path $zapysLib $_ }
$zapysClasspath = $zapysCompileClasspath -join [IO.Path]::PathSeparator
$zapysSources = @(Get-ChildItem (Join-Path $zapysRoot 'android/app/src/main/java/ua/zapys/domain') -Filter '*.kt' -Recurse)
$zapysTestFiles = @(Get-ChildItem (Join-Path $zapysRoot 'android/app/src/test/java/ua/zapys/domain') -Filter '*Test.kt' -Recurse)
if ($Tests) { $zapysTestFiles = @($zapysTestFiles | Where-Object { $_.BaseName -in $Tests }) }
if ($zapysTestFiles.Count -eq 0) { throw 'No domain tests selected' }
$zapysSourcePaths = @($zapysSources.FullName) + @($zapysTestFiles.FullName)
& java -cp "$zapysLib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath $zapysClasspath -d $zapysClasses @zapysSourcePaths
if ($LASTEXITCODE -ne 0) { throw 'Offline domain compilation failed' }
$zapysTestClasses = @($zapysTestFiles | ForEach-Object { 'ua.zapys.domain.' + $_.BaseName })
& java -cp ($zapysClasses + [IO.Path]::PathSeparator + $zapysClasspath) org.junit.runner.JUnitCore @zapysTestClasses
if ($LASTEXITCODE -ne 0) { throw 'Domain tests failed' }
Write-Output 'Offline domain checks passed (Kotlin 2.0.20); Android Gradle/SDK gates remain separate.'
