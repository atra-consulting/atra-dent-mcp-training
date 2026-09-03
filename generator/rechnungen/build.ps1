#Requires -Version 5.1

# Windows equivalent of generator/rechnungen/build.sh.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
Set-Location $root

# $ErrorActionPreference does not reach a native command: typst reports failure
# through its exit code and PowerShell carries on regardless. Without this the
# count below would report Rechnungen that were never written.
function Assert-LastExitCode([string]$what) {
  if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) {
    [Console]::Error.WriteLine("FEHLER: $what ist mit Exit-Code $LASTEXITCODE fehlgeschlagen.")
    exit 1
  }
}

$targetDirectory = 'submissions/rechnungen/pdf'
New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null

if ($args.Count -gt 0 -and $args[0] -eq '--pruefen') {
  $caseFile = 'generator/rechnungen/tests/wrap-case.yaml'
  $targetDirectory = 'generator/rechnungen/pruefung'
  New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null

  Write-Host "Prüfmodus: Umbruchtest über alle Stile"
  foreach ($style in @('klassisch', 'modern', 'verrechnungsstelle', 'landpraxis', 'klinik')) {
    $target = "$targetDirectory/umbruch-$style.pdf"
    Write-Host ("  {0,-20} -> {1}" -f $style, $target)
    & typst compile --root . --pdf-standard a-2b `
      --input "fall=/$caseFile" `
      --input "stil=$style" `
      generator/rechnungen/rechnung.typ $target
    Assert-LastExitCode "typst compile im Stil $style"
  }
  Write-Host ""
  Write-Host "Fertig. Fünf Fassungen in $targetDirectory/ — Umbrüche von Hand ansehen."
  exit 0
}

Write-Host "Rechnungen"
$count = 0
$sources = Get-ChildItem -LiteralPath 'submissions/rechnungen/cases' -Filter '*.yaml' -ErrorAction SilentlyContinue
foreach ($source in $sources) {
  $name = $source.BaseName
  $target = "$targetDirectory/$name.pdf"
  Write-Host ("  {0,-34} -> {1}" -f "$name.yaml", $target)
  & typst compile --root . --pdf-standard a-2b --input "fall=/submissions/rechnungen/cases/$($source.Name)" generator/rechnungen/rechnung.typ $target
  Assert-LastExitCode "typst compile von $($source.Name)"
  $count++
}

Write-Host ""
Write-Host "Fertig. $count Rechnung(en) in $targetDirectory/"
