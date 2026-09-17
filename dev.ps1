param(
    [ValidateSet('build', 'client', 'server', 'datagen', 'sources', 'test', 'client-test')]
    [string]$Task = 'build'
)

$ErrorActionPreference = 'Stop'
$previousJavaHome = $env:JAVA_HOME
try {
    $candidates = @($env:JAVA_HOME)
    $jdkDirectory = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path -LiteralPath $jdkDirectory) {
        $candidates += Get-ChildItem -LiteralPath $jdkDirectory -Directory | Select-Object -ExpandProperty FullName
    }
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $candidates += Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
    }
    $selectedJdk = $null
    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        $releaseFile = Join-Path $candidate 'release'
        if (-not (Test-Path -LiteralPath $releaseFile)) { continue }
        $release = Get-Content -LiteralPath $releaseFile -Raw
        if ($release -match 'JAVA_VERSION="(\d+)' -and [int]$Matches[1] -ge 25 -and
            (Test-Path -LiteralPath (Join-Path $candidate 'bin/javac.exe'))) {
            $selectedJdk = $candidate
            break
        }
    }
    if (-not $selectedJdk) {
        throw 'JDK 25 oder neuer fehlt. Bitte installieren und JAVA_HOME auf den JDK-Ordner setzen.'
    }
    $env:JAVA_HOME = $selectedJdk
    $gradleTask = @{
        build = 'build'; client = 'runClient'; server = 'runServer'
        datagen = 'runDatagen'; sources = 'genSources'
        test = 'runGameTest'; 'client-test' = 'runClientGameTest'
    }[$Task]
    Write-Host "JDK: $selectedJdk"
    & "$PSScriptRoot/gradlew.bat" --project-dir "$PSScriptRoot" $gradleTask
    if ($LASTEXITCODE -ne 0) { throw "Gradle fehlgeschlagen (Exitcode $LASTEXITCODE)." }
} finally {
    $env:JAVA_HOME = $previousJavaHome
}
