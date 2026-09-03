#Requires -Version 5.1

# Windows equivalent of generator/nachweise/build.sh.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
Set-Location $root

# $ErrorActionPreference does not reach a native command: typst reports failure
# through its exit code and PowerShell carries on regardless. Without this the
# count below would report Einreichungen that were never written.
function Assert-LastExitCode([string]$what) {
  if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) {
    [Console]::Error.WriteLine("FEHLER: $what ist mit Exit-Code $LASTEXITCODE fehlgeschlagen.")
    exit 1
  }
}

$documents = @('befund', 'historie', 'fragebogen')

if ($args.Count -gt 0 -and $args[0] -eq '--pruefen') {
  $caseFile = if ($args.Count -gt 1) { $args[1] } else { 'submissions/nachweise/cases/10002-schuster.yaml' }
  $targetDirectory = 'generator/nachweise/pruefung'
  New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null

  Write-Host "Prüfmodus: $(Split-Path -Leaf $caseFile) über alle Stile"
  foreach ($style in @('klassisch', 'modern', 'mvz', 'landpraxis', 'klinik')) {
    $target = "$targetDirectory/befund-$style.pdf"
    Write-Host ("  {0,-14} -> {1}" -f $style, $target)
    & typst compile --root . --pdf-standard a-2b `
      --input "fall=/$caseFile" `
      --input "stil=$style" `
      generator/nachweise/befund.typ $target
    Assert-LastExitCode "typst compile im Stil $style"
  }
  Write-Host ""
  Write-Host "Fertig. Fünf Fassungen in $targetDirectory/ — Umbrüche von Hand ansehen."
  exit 0
}

Write-Host "Nachweise"
$count = 0
$sources = Get-ChildItem -LiteralPath 'submissions/nachweise/cases' -Filter '*.yaml' -ErrorAction SilentlyContinue
foreach ($source in $sources) {
  $name = $source.BaseName
  $targetDirectory = "submissions/nachweise/pdf/$name"
  New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null
  Write-Host ("  {0,-24} ->" -f $name) -NoNewline
  foreach ($document in $documents) {
    & typst compile --root . --pdf-standard a-2b `
      --input "fall=/submissions/nachweise/cases/$($source.Name)" `
      "generator/nachweise/$document.typ" "$targetDirectory/$document.pdf"
    if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) { Write-Host "" }
    Assert-LastExitCode "typst compile von $document fuer $name"
    Write-Host " $document" -NoNewline
  }
  Write-Host ""
  $count++
}

Write-Host ""
Write-Host "Fertig. $count Einreichung(en), $($count * $documents.Count) Dokumente in submissions/nachweise/pdf/"
