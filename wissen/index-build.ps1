#Requires -Version 5.1

# Windows equivalent of wissen/index-build.sh.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

function Write-ErrorAndExit([string]$message) {
  [Console]::Error.WriteLine("FEHLER: $message")
  exit 1
}

# $ErrorActionPreference does not reach a native command: mvn reports failure
# through its exit code and PowerShell carries on regardless. This is the
# "set -e" that wissen/index-build.sh gets from its shebang line.
function Assert-LastExitCode([string]$what) {
  if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) {
    Write-ErrorAndExit "$what ist mit Exit-Code $LASTEXITCODE fehlgeschlagen."
  }
}

# "java -version" writes its banner to stderr, and under Windows PowerShell
# 5.1 a native command's stderr turns into a terminating NativeCommandError as
# soon as it is merged into the success stream while $ErrorActionPreference is
# 'Stop'. The preference is function-local here, so lowering it covers exactly
# this one call and nothing after it.
function Get-JavaVersionOutput {
  $ErrorActionPreference = 'Continue'
  return (& java -version 2>&1 | Out-String)
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
  Write-ErrorAndExit "'java' wurde nicht gefunden. Der Indexbau braucht Java 25 (LTS), z. B. Eclipse Temurin: https://adoptium.net."
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
  Write-ErrorAndExit "'mvn' wurde nicht gefunden. Der Wissensdienst wird mit Maven gebaut: https://maven.apache.org (Windows: es gibt kein winget-Paket -- Binary-ZIP von https://maven.apache.org/download.cgi entpacken und dessen bin-Verzeichnis in den PATH aufnehmen)."
}

$versionOutput = Get-JavaVersionOutput
$majorVersion = $null
if ($versionOutput -match 'version "(\d+)') { $majorVersion = [int]$Matches[1] }
if (-not $majorVersion -or $majorVersion -lt 25) {
  $shown = if ($majorVersion) { $majorVersion } else { 'unbekannter Version' }
  Write-ErrorAndExit "Java $shown ist zu alt. Der Wissensdienst verlangt Java 25."
}

$htmlCount = 0
if (Test-Path -LiteralPath 'wissen/generated/html') {
  $htmlCount = (Get-ChildItem -LiteralPath 'wissen/generated/html' -Filter '*.html' -ErrorAction SilentlyContinue).Count
}
if ($htmlCount -eq 0) {
  Write-ErrorAndExit "Keine HTML-Fassungen unter wissen/generated/html/. Zuerst .\wissen\dokumente-build.ps1 ausführen."
}

$env:SPRING_AI_EMBEDDING_TRANSFORMER_CACHE_DIRECTORY = Join-Path $root 'wissen/modell'

if (-not (Test-Path -LiteralPath 'wissen/modell') -or -not (Get-ChildItem -LiteralPath 'wissen/modell' -ErrorAction SilentlyContinue)) {
  Write-Host "Erster Lauf: das Einbettungsmodell (rund 490 MB) wird nach wissen/modell/ geladen -- das dauert einmalig."
}

& mvn -B -q -f produktmodell/pom.xml install -DskipTests
Assert-LastExitCode 'mvn install von produktmodell'

Write-Host "Baue die Vektorindizes (Spring-Profil indexbau)."
& mvn -q -f wissen/dienst/pom.xml spring-boot:run '-Dspring-boot.run.profiles=indexbau'
Assert-LastExitCode 'Der Indexbau'

Write-Host ""
Write-Host "Fertig. Indizes unter wissen/generated/index/:"
Get-ChildItem -LiteralPath 'wissen/generated/index' -Filter '*.json' |
  ForEach-Object { "{0,10:N0}  {1}" -f $_.Length, $_.Name } |
  Write-Host
