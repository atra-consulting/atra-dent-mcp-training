#Requires -Version 5.1

# Windows equivalent of generator/rechnungen/tests/pruefen.sh.

$root = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)))
Set-Location $root

$probe = 'generator/rechnungen/tests/sonde.typ'
$failures = 0

# Only the exit code is of interest, never the rendering -- the probe is here
# to say whether a case file passes the Typst-side validation. bash throws the
# page away with /dev/null; a real temp file is the safer equivalent than the
# NUL device, which would have typst write to a path without an extension.
$discard = Join-Path ([System.IO.Path]::GetTempPath()) ("atra-sonde-$PID.png")

function Invoke-Probe([string]$fall) {
  $output = & typst compile --root . --input "fall=$fall" --format png $probe $discard 2>&1
  return @{ Success = ($LASTEXITCODE -eq 0); Output = $output }
}

Write-Host "Gute Fälle (müssen durchlaufen)"
$validCases = Get-ChildItem -LiteralPath 'generator/rechnungen/tests/cases-valid' -Filter '*.yaml' -ErrorAction SilentlyContinue
foreach ($f in $validCases) {
  $path = "/$($f.FullName.Substring($root.Length + 1) -replace '\\', '/')"
  $result = Invoke-Probe $path
  if ($result.Success) {
    Write-Host ("  OK      {0}" -f $f.Name)
  } else {
    Write-Host ("  FEHLER  {0} — bricht ab, sollte aber durchlaufen" -f $f.Name)
    ($result.Output | Out-String) -split "`n" | ForEach-Object { Write-Host "          $_" }
    $failures++
  }
}

Write-Host ""
Write-Host "Fehlerhafte Fälle (müssen abbrechen)"
$invalidCases = Get-ChildItem -LiteralPath 'generator/rechnungen/tests/cases-invalid' -Filter '*.yaml' -ErrorAction SilentlyContinue
foreach ($f in $invalidCases) {
  $path = "/$($f.FullName.Substring($root.Length + 1) -replace '\\', '/')"
  $result = Invoke-Probe $path
  if ($result.Success) {
    Write-Host ("  FEHLER  {0} — läuft durch, sollte aber abbrechen" -f $f.Name)
    $failures++
  } else {
    Write-Host ("  OK      {0}" -f $f.Name)
  }
}

Remove-Item -LiteralPath $discard -Force -ErrorAction SilentlyContinue

Write-Host ""
if ($failures -eq 0) {
  Write-Host "Alle Prüfungen bestanden."
} else {
  Write-Host "$failures Prüfung(en) fehlgeschlagen."
  exit 1
}
