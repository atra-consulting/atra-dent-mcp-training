#Requires -Version 5.1

# Windows equivalent of setup.sh. Checks the tools and installs the
# dependencies of every subproject.
#
#   .\setup.ps1           check and install
#   .\setup.ps1 --hilfe   this overview

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

function Write-ErrorAndExit([string]$message) {
  [Console]::Error.WriteLine("FEHLER: $message")
  exit 1
}

function Show-Help {
  @'
Sets the lab up: checks the tools and installs the dependencies of every
subproject.

  .\setup.ps1           check and install
  .\setup.ps1 --hilfe   this overview

Checked:
  java 25, mvn         Rechenkern, Kernsystem, Wissensdienst, agenten
  node 20.19+, npm     sachbearbeiter-ui, kunden-chat
  typst, python        hint only -- rebuilding the documents, the Rechnung form
  .env                 hint only -- GEMINI_API_KEY for the three agents

Nothing here needs curl or lsof, unlike setup.sh: start.ps1 asks for the
health endpoints with Invoke-WebRequest and reads the ports with
Get-NetTCPConnection, both of which Windows brings along.

Installed:
  mvn install -DskipTests      produktmodell (shared module, without tests)
  mvn dependency:go-offline    rechenkern, kernsystem, wissen/dienst
  mvn install -DskipTests      agenten (reactor, as in start.ps1)
  npm install                  sachbearbeiter-ui, kunden-chat

Missing tools are collected and reported together at the end; whatever can be
installed with what is there is installed. If something is missing the script
ends with exit code 1 -- just run it again after installing.
'@
}

$firstArg = if ($args.Count -gt 0) { $args[0] } else { '' }
switch ($firstArg) {
  { $_ -in @('-h', '--hilfe', '--help') } {
    Show-Help
    exit 0
  }
  '' { }
  default {
    Write-ErrorAndExit "Unbekannte Option '$($args[0])'. '.\setup.ps1 --hilfe' zeigt die Möglichkeiten."
  }
}

function Write-Warn([string]$message) {
  [Console]::Error.WriteLine("Hinweis: $message")
}

# $ErrorActionPreference does not reach a native command: mvn and npm report
# failure through their exit code and PowerShell carries on regardless. This is
# the "set -e" that setup.sh gets from its shebang line.
function Assert-LastExitCode([string]$what) {
  if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) {
    Write-ErrorAndExit "$what ist mit Exit-Code $LASTEXITCODE fehlgeschlagen."
  }
}

$missing = New-Object System.Collections.Generic.List[string]
$hints = New-Object System.Collections.Generic.List[string]

$javaReady = $false
$nodeReady = $false

# "java -version" writes its banner to stderr, and under Windows PowerShell
# 5.1 a native command's stderr turns into a terminating NativeCommandError as
# soon as it is merged into the success stream while $ErrorActionPreference is
# 'Stop'. The preference is function-local here, so lowering it covers exactly
# this one call and nothing after it.
function Get-JavaVersionOutput {
  $ErrorActionPreference = 'Continue'
  return (& java -version 2>&1 | Out-String)
}

function Test-Java {
  $ok = $true
  $tool = Get-Command java -ErrorAction SilentlyContinue
  if ($tool) {
    $versionOutput = Get-JavaVersionOutput
    $majorVersion = $null
    if ($versionOutput -match 'version "(\d+)') { $majorVersion = [int]$Matches[1] }
    if (-not $majorVersion -or $majorVersion -lt 25) {
      $ok = $false
      $shown = if ($majorVersion) { $majorVersion } else { 'unbekannt' }
      $missing.Add("java -- Version $shown ist zu alt, die Dienste verlangen Java 25 (LTS): https://adoptium.net")
    }
  } else {
    $ok = $false
    $missing.Add("java -- Die Dienste brauchen Java 25 (LTS), z. B. Eclipse Temurin: https://adoptium.net")
  }
  if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    $ok = $false
    $missing.Add("mvn -- Die Dienste werden mit Maven gebaut und gestartet: https://maven.apache.org (Windows: es gibt kein winget-Paket -- Binary-ZIP von https://maven.apache.org/download.cgi entpacken und dessen bin-Verzeichnis in den PATH aufnehmen)")
  }
  if ($ok) { $script:javaReady = $true }
}

function Test-Node {
  $ok = $true
  $tool = Get-Command node -ErrorAction SilentlyContinue
  if ($tool) {
    $version = (& node --version).TrimStart('v')
    $parts = $version.Split('.')
    $major = [int]$parts[0]
    $minor = [int]$parts[1]
    if ($major -lt 20 -or ($major -eq 20 -and $minor -lt 19)) {
      $ok = $false
      $missing.Add("node -- Version $version ist zu alt, Vite 8 verlangt 20.19+ oder 22.12+: https://nodejs.org")
    }
  } else {
    $ok = $false
    $missing.Add("node -- Die Oberflächen brauchen Node.js 20.19+ (oder 22.12+): https://nodejs.org (Windows: winget install OpenJS.NodeJS.LTS)")
  }
  if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    $ok = $false
    $missing.Add("npm -- gehört zu Node.js; eine unvollständige Installation bitte erneuern")
  }
  if ($ok) { $script:nodeReady = $true }
}

function Test-OptionalTool([string]$name, [string]$purpose) {
  if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
    $hints.Add("$name fehlt -- $purpose")
  }
}

Write-Host "Pruefe Werkzeuge."
Test-Java
Test-Node

Test-OptionalTool typst "only for .\wissen\build.ps1, .\generator\rechnungen\build.ps1, .\generator\nachweise\build.ps1 and .\generator\reconcile.ps1; the finished documents are checked in under wissen/generated/ and submissions/: https://typst.app"

# Any of the three will do, and .\generator\reconcile.ps1 looks for them in
# this order too. "py" first because it is the launcher that comes with the
# installer from python.org, and because "python3" on a machine without Python
# is usually the Store stub that opens the Store instead of running anything.
if (-not (Get-Command py -ErrorAction SilentlyContinue) -and
    -not (Get-Command python -ErrorAction SilentlyContinue) -and
    -not (Get-Command python3 -ErrorAction SilentlyContinue)) {
  $hints.Add("python fehlt -- only for the Rechnung form of the generator (generator/rechnungen/frontend.py) and generator/reconcile.ps1, whose --goae-only mode needs only Python: https://python.org (Windows: winget install Python.Python.3.13)")
}

if (-not (Test-Path -LiteralPath '.env' -PathType Leaf)) {
  $hints.Add(".env fehlt -- Orchestrator, Beratungsagent und Schadensfallagent brauchen GEMINI_API_KEY, und ein fremder Agent, dessen Karte einen Schluessel verlangt, braucht ihn in seinem Katalogeintrag -- fuer den Arztservice AGENTEN_ARZTSERVICE_API_KEY: Copy-Item .env.beispiel .env und Schluessel eintragen")
}

if (-not (Test-Path -LiteralPath 'wissen/modell' -PathType Container) -or -not (Get-ChildItem -LiteralPath 'wissen/modell' -ErrorAction SilentlyContinue)) {
  $hints.Add("Der Wissensdienst laedt beim ersten Start sein Einbettungsmodell (rund 490 MB) nach wissen/modell/ -- das dauert einmalig laenger")
}

Write-Host ""

if ($javaReady) {
  Write-Host "Maven lädt die Abhängigkeiten der Java-Dienste (beim ersten Mal einige Minuten, Ausgabe nur bei Fehlern)."
  & mvn -B -q -f produktmodell/pom.xml install -DskipTests
  Assert-LastExitCode 'mvn install von produktmodell'
  foreach ($pom in @('rechenkern/pom.xml', 'kernsystem/pom.xml', 'wissen/dienst/pom.xml')) {
    Write-Host "  $($pom -replace '/pom\.xml$', '')"
    & mvn -B -q -f $pom dependency:go-offline
    Assert-LastExitCode "mvn dependency:go-offline fuer $pom"
  }

  Write-Host "  agenten (mvn install im Reaktor, ohne Tests)"
  & mvn -B -q -f agenten/pom.xml install -DskipTests
  Assert-LastExitCode 'mvn install im Reaktor agenten'
} else {
  Write-Warn "Java-Dienste übersprungen (rechenkern, kernsystem, wissen, agenten) -- Java oder Maven fehlt, siehe unten."
}

Write-Host ""

if ($nodeReady) {
  foreach ($directory in @('sachbearbeiter-ui', 'kunden-chat')) {
    Write-Host "npm install in $directory."
    & npm --prefix $directory install
    Assert-LastExitCode "npm install in $directory"
  }
} else {
  Write-Warn "Oberflächen übersprungen (sachbearbeiter-ui, kunden-chat) -- Node.js oder npm fehlt, siehe unten."
}

Write-Host ""

if ($hints.Count -gt 0) {
  Write-Host "Hinweise:"
  foreach ($hint in $hints) {
    Write-Host "  - $hint"
  }
  Write-Host ""
}

if ($missing.Count -gt 0) {
  Write-Host "Es fehlt:"
  foreach ($entry in $missing) {
    Write-Host "  - $entry"
  }
  Write-Host ""
  Write-Host "Nach der Installation .\setup.ps1 erneut ausführen."
  exit 1
}

Write-Host "Alles eingerichtet. .\start.ps1 startet das Lab."
