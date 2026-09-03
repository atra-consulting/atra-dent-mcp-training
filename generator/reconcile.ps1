#Requires -Version 5.1

# Windows equivalent of generator/reconcile.sh.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

function Write-ErrorAndExit([string]$message) {
  [Console]::Error.WriteLine("FEHLER: $message")
  exit 1
}

$goaeOnly = $false
foreach ($arg in $args) {
  switch ($arg) {
    '--goae-only' { $goaeOnly = $true }
    default {
      Write-ErrorAndExit "unbekannte Option '$arg'."
    }
  }
}

# "py" first because it is the launcher the installer from python.org puts on
# the PATH, and because "python3" on a machine without Python is usually the
# Microsoft Store stub: it exists, it resolves, and calling it opens the Store
# instead of running anything.
$python = $null
$pythonArguments = @()
if (Get-Command py -ErrorAction SilentlyContinue) {
  $python = 'py'
  $pythonArguments = @('-3')
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
  $python = 'python'
} elseif (Get-Command python3 -ErrorAction SilentlyContinue) {
  $python = 'python3'
} else {
  Write-ErrorAndExit "'python' wurde nicht gefunden."
}

$failures = 0

if (-not $goaeOnly) {
  if (-not (Get-Command typst -ErrorAction SilentlyContinue)) {
    Write-ErrorAndExit "'typst' wurde nicht gefunden. Typst erzeugt die Rechnungen: https://typst.app (Windows: winget install Typst.Typst)."
  }

  # See normalize.py for why the fresh and the committed PDF are stripped
  # before the comparison rather than compared byte for byte as they stand.
  $normalizePy = Join-Path $root 'generator/normalize.py'

  $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ([System.IO.Path]::GetRandomFileName())
  New-Item -ItemType Directory -Path $tempDir | Out-Null
  try {
    Write-Host "Rechnungen: Neubau gegen submissions/rechnungen/pdf/"
    $caseFiles = Get-ChildItem -LiteralPath 'submissions/rechnungen/cases' -Filter '*.yaml' -ErrorAction SilentlyContinue
    foreach ($caseFile in $caseFiles) {
      $name = $caseFile.BaseName
      $fresh = Join-Path $tempDir "$name.pdf"
      $committed = "submissions/rechnungen/pdf/$name.pdf"

      & typst compile --root . --pdf-standard a-2b --input "fall=/submissions/rechnungen/cases/$($caseFile.Name)" `
        generator/rechnungen/rechnung.typ $fresh
      if ($LASTEXITCODE -ne 0) {
        [Console]::Error.WriteLine("  FEHLER     $name.pdf liess sich nicht neu bauen")
        $failures = 1
        continue
      }

      if (-not (Test-Path -LiteralPath $committed)) {
        [Console]::Error.WriteLine("  FEHLT      $name.pdf ist unter submissions/rechnungen/pdf/ nicht abgelegt")
        $failures = 1
        continue
      }

      & $python @pythonArguments $normalizePy $fresh $committed
      if ($LASTEXITCODE -eq 0) {
        Write-Host ("  OK         {0}.pdf" -f $name)
      } else {
        [Console]::Error.WriteLine("  ABWEICHUNG $name.pdf weicht vom abgelegten Stand ab")
        $failures = 1
      }
    }
    Write-Host ""
  } finally {
    Remove-Item -LiteralPath $tempDir -Recurse -Force -ErrorAction SilentlyContinue
  }
}

Write-Host "GOAe-Nummern: wissen/daten/goae-auszug.yaml gegen submissions/rechnungen/cases/"
& $python @pythonArguments (Join-Path $root 'generator/goae_reconcile.py') $root
if ($LASTEXITCODE -ne 0) { $failures = 1 }

Write-Host ""
if ($failures -eq 0) {
  if ($goaeOnly) {
    Write-Host "Fertig. GOAe-Auszug stimmt mit submissions/rechnungen/cases/ ueberein."
  } else {
    Write-Host "Fertig. Rechnungen und GOAe-Auszug stimmen mit submissions/ ueberein."
  }
} else {
  [Console]::Error.WriteLine("Abgleich fehlgeschlagen, siehe oben.")
}
exit $failures
