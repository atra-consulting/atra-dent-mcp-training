#Requires -Version 5.1

# Windows equivalent of wissen/dokumente-build.sh.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

function Write-ErrorAndExit([string]$message) {
  [Console]::Error.WriteLine("FEHLER: $message")
  exit 1
}

# $ErrorActionPreference does not reach a native command: typst reports failure
# through its exit code and PowerShell carries on regardless. Without this the
# script would count the documents still lying in wissen/generated/ from the
# last successful run and end on "Fertig." after having built nothing.
function Assert-LastExitCode([string]$what) {
  if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) {
    Write-ErrorAndExit "$what ist mit Exit-Code $LASTEXITCODE fehlgeschlagen."
  }
}

if (-not (Get-Command typst -ErrorAction SilentlyContinue)) {
  Write-ErrorAndExit "'typst' wurde nicht gefunden. Typst erzeugt die Bedingungswerke: https://typst.app (Windows: winget install Typst.Typst)."
}

# Typst stempelt sonst die Uhrzeit des Bauens in jedes PDF, und dieselben
# Quellen ergaeben bei jedem Lauf andere Dateien. Die Dokumente tragen deshalb
# das Datum, ab dem die Tarifdaten gelten -- damit sind sie reproduzierbar und
# aendern sich nur, wenn sich ihr Inhalt aendert.
$stand = $null
foreach ($line in Get-Content -LiteralPath 'wissen/daten/tarife.yaml') {
  if ($line -match '^stand:\s*(\S+)') {
    $stand = $Matches[1]
    break
  }
}
if ($stand) {
  try {
    $parsed = [DateTime]::ParseExact($stand, 'yyyy-MM-dd', [System.Globalization.CultureInfo]::InvariantCulture)
    $utcMidnight = [DateTime]::SpecifyKind($parsed, [DateTimeKind]::Utc)
    $epoch = [DateTimeOffset]::new($utcMidnight).ToUnixTimeSeconds()
    $env:SOURCE_DATE_EPOCH = "$epoch"
  } catch {
    # Malformed "stand:" value -- leave SOURCE_DATE_EPOCH unset, same as the
    # bash version's "|| true" fallback.
  }
}

$documents = @(
  'atra-dent-smart-avb',
  'atra-dent-balance-avb',
  'atra-dent-brillant-avb',
  'atra-dent-tarifvergleich',
  'atra-dent-goz-zuordnung',
  'atra-dent-beratungshandbuch'
)

$pdfDirectory = 'wissen/generated/pdf'
$htmlDirectory = 'wissen/generated/html'
New-Item -ItemType Directory -Force -Path $pdfDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $htmlDirectory | Out-Null

Write-Host "PDF-Fassung (für den Menschen)"
foreach ($name in $documents) {
  $source = "wissen/dokumente/$name.typ"
  $target = "$pdfDirectory/$name.pdf"
  Write-Host ("  {0,-32} -> {1}" -f "$name.typ", $target)
  & typst compile --root . $source $target
  Assert-LastExitCode "typst compile von $source"
}

Write-Host ""
Write-Host "HTML-Fassung (für den Wissensdienst)"
foreach ($name in $documents) {
  $source = "wissen/dokumente/$name.typ"
  $target = "$htmlDirectory/$name.html"
  Write-Host ("  {0,-32} -> {1}" -f "$name.typ", $target)
  & typst compile --root . --features html --format html $source $target
  Assert-LastExitCode "typst compile von $source nach HTML"
}

Write-Host ""
$pdfCount = (Get-ChildItem -LiteralPath $pdfDirectory -Filter '*.pdf').Count
$htmlCount = (Get-ChildItem -LiteralPath $htmlDirectory -Filter '*.html').Count
Write-Host "Fertig. $pdfCount PDF in $pdfDirectory/, $htmlCount HTML in $htmlDirectory/"
