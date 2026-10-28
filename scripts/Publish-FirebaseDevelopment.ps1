param(
    [string]$TesterGroups,
    [string]$TesterEmails,

    [string]$ReleaseNotes = "Widget Palette development update"
)

$ErrorActionPreference = "Stop"
if ($TesterGroups -and $TesterEmails) {
    throw 'Specify tester groups or tester emails, not both.'
}
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$versionFile = Join-Path $projectRoot 'development-version.txt'
$firebaseConfigFile = Join-Path $projectRoot 'app\google-services.json'

if (-not (Test-Path -LiteralPath $firebaseConfigFile)) {
    throw "Firebase config not found: $firebaseConfigFile"
}

$firebaseConfig = Get-Content -LiteralPath $firebaseConfigFile -Raw | ConvertFrom-Json
$registeredPackages = @(
    $firebaseConfig.client |
        ForEach-Object { $_.client_info.android_client_info.package_name }
)
if ($registeredPackages -notcontains 'com.company.widgetspalette') {
    throw "google-services.json must register com.company.widgetspalette; found: $($registeredPackages -join ', ')"
}
$firebaseAppId = $firebaseConfig.client |
    Where-Object { $_.client_info.android_client_info.package_name -eq 'com.company.widgetspalette' } |
    Select-Object -First 1 -ExpandProperty client_info |
    Select-Object -ExpandProperty mobilesdk_app_id
if (-not $firebaseAppId) {
    throw 'Firebase App ID for com.company.widgetspalette was not found in google-services.json'
}

$gradleCandidates = @(
    (Join-Path $projectRoot 'gradlew.bat'),
    (Join-Path $projectRoot 'work\gradle-8.7\gradle-8.7\bin\gradle.bat')
)
$gradle = $gradleCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $gradle) {
    $gradleCommand = Get-Command gradle.bat -ErrorAction SilentlyContinue
    if (-not $gradleCommand) {
        $gradleCommand = Get-Command gradle -ErrorAction SilentlyContinue
    }
    if (-not $gradleCommand) {
        throw 'Gradle 8.7 is required. Add the project Gradle wrapper or install Gradle 8.7.'
    }
    $gradle = $gradleCommand.Source
}

$currentVersionCode = 1
if (Test-Path -LiteralPath $versionFile) {
    $versionText = (Get-Content -LiteralPath $versionFile -Raw).Trim()
    if (-not [int]::TryParse($versionText, [ref]$currentVersionCode)) {
        throw "Invalid development version code in $versionFile"
    }
}
if ($currentVersionCode -ge 2100000000) {
    throw 'Android versionCode limit reached.'
}

$nextVersionCode = $currentVersionCode + 1

$gradleArgs = @(
    ':app:assembleDevelopment',
    ':app:appDistributionUploadDevelopment',
    "-PdevelopmentVersionCode=$nextVersionCode",
    "-PfirebaseReleaseNotes=$ReleaseNotes",
    '--no-daemon'
)
if ($TesterGroups) {
    $gradleArgs += "-PfirebaseGroups=$TesterGroups"
}
if ($TesterEmails) {
    $gradleArgs += "-PfirebaseTesters=$TesterEmails"
}

Push-Location $projectRoot
try {
    & $gradle @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Firebase App Distribution upload failed (Gradle exit code $LASTEXITCODE). The version code was not advanced."
    }
    Set-Content -LiteralPath $versionFile -Value $nextVersionCode -NoNewline
} finally {
    Pop-Location
}
