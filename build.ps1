# Builds the Jarvis app. Usage: .\build.ps1 assembleDebug | test | clean ...
# Uses JAVA_HOME / ANDROID_HOME from the environment (JDK 17 + Android SDK with platform 35 and
# build-tools 35). On Linux/macOS just run ./gradlew assembleDebug.
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArgs)

Set-Location $PSScriptRoot
if (-not $env:ANDROID_HOME -and $env:ANDROID_SDK_ROOT) { $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT }
if (-not $env:ANDROID_HOME -and -not (Test-Path "$PSScriptRoot\local.properties")) {
    Write-Error "Set ANDROID_HOME to your Android SDK folder (or create local.properties with sdk.dir=...)."
    exit 1
}
& "$PSScriptRoot\gradlew.bat" @GradleArgs
exit $LASTEXITCODE
