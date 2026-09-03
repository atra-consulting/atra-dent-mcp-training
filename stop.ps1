#Requires -Version 5.1

# Windows equivalent of stop.sh. Beendet das Lab, falls Strg+C nicht gereicht
# hat.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

. "$root/components.ps1"

function Write-ErrorAndExit([string]$message) {
  [Console]::Error.WriteLine("FEHLER: $message")
  exit 1
}

function Show-Help {
  @"
Beendet das Lab. Fuer den Fall, dass Strg+C nicht gereicht hat.

  .\stop.ps1                        alles
  .\stop.ps1 wissen kernsystem      nur die genannten
  .\stop.ps1 --hilfe                diese Uebersicht

Gearbeitet wird in drei Schritten, und jeder darf leer ausgehen:

  1. Ein noch laufendes .\start.ps1 (als eigener PowerShell-Prozessbaum
     erkannt, der zu diesem Repository gehoert) wird mitsamt allem, was es
     gestartet hat, beendet. Anders als unter macOS/Linux gibt es unter
     Windows kein SIGTERM, dem ein Skript selbst mit einem eigenen Trap
     begegnen koennte -- deshalb geht dieses Skript hier denselben Weg wie
     start.ps1 selbst und beendet den Prozessbaum direkt.
  2. Was dann noch auf einem Port des Labs lauscht und zu diesem Repository
     gehoert, wird beendet. Das sind die acht Ports der Bausteine und die
     acht Debug-Ports, die .\start.ps1 --debug oeffnet. Wer den Port nur
     benutzt -- ein Debugger am Debug-Port, ein Browser auf der Oberflaeche
     -- ist kein Ziel.
  3. Uebrig gebliebene Prozesse des Labs -- ein mvn ohne Port, eine Vite, die
     sich nicht mehr meldet -- werden anhand ihres Arbeitsverzeichnisses
     gefunden und beendet.

Schritt 1 und 3 laufen nur, wenn kein Baustein genannt ist: Wer gezielt
".\stop.ps1 kunden-chat" sagt, will das uebrige Lab behalten.

Gefunden wird ausschliesslich, was zu diesem Repository gehoert -- ueber die
Ports aus .env und dieser Datei und ueber Prozesse, deren Arbeitsverzeichnis
unter

  $root

liegt. Ein Maven oder ein Node aus einem anderen Projekt bleibt unbehelligt.

Der Arztservice wird nicht gestoppt. Er laeuft fremd gehostet und war nie ein
Prozess auf diesem Rechner.
"@
}

$selected = New-Object System.Collections.Generic.List[string]
foreach ($arg in $args) {
  switch -Regex ($arg) {
    '^(-h|--hilfe|--help)$' {
      Show-Help
      exit 0
    }
    '^-' {
      Write-ErrorAndExit "Unbekannte Option '$arg'. '.\stop.ps1 --hilfe' zeigt die Moeglichkeiten."
    }
    default {
      if (-not (Test-IsComponent $arg)) {
        Write-ErrorAndExit "Unbekannter Baustein '$arg'. Moeglich: $($Components -join ' ')"
      }
      if (-not $selected.Contains($arg)) { $selected.Add($arg) }
    }
  }
}

$everything = $false
if ($selected.Count -eq 0) {
  $everything = $true
  $selected = [System.Collections.Generic.List[string]]::new([string[]]$Components)
}

$stopped = 0

$ownPid = $PID
$ownParentPid = (Get-CimInstance Win32_Process -Filter "ProcessId=$ownPid" -ErrorAction SilentlyContinue).ParentProcessId

function Test-IsOwn([int]$processId) {
  return ($processId -eq $ownPid) -or ($processId -eq $ownParentPid)
}

function Test-Alive([int]$processId) {
  return $null -ne (Get-Process -Id $processId -ErrorAction SilentlyContinue)
}

function Wait-Gone([int]$processId, [int]$limitSeconds = 5) {
  $seconds = 0
  while ($seconds -lt $limitSeconds) {
    if (-not (Test-Alive $processId)) { return $true }
    Start-Sleep -Seconds 1
    $seconds++
  }
  return -not (Test-Alive $processId)
}

# --- 1. a still-running start.ps1, together with everything it started ------

function Stop-StartScript {
  # Excludes "__worker__" invocations: those are start.ps1's own component
  # children (see start.ps1's worker-mode comment), not the top-level
  # orchestrator. Stop-ProcessTree below already takes every one of them down
  # together with the orchestrator; matching them here too would just be
  # redundant, not wrong, but this keeps step 1 doing exactly what it says.
  $candidates = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -match 'start\.ps1' -and $_.CommandLine -notmatch '__worker__' }
  if (-not $candidates) { return }

  $found = $false
  foreach ($candidate in $candidates) {
    $processId = [int]$candidate.ProcessId
    if (Test-IsOwn $processId) { continue }
    if (-not (Test-BelongsToLab $processId $candidate.CommandLine)) { continue }
    if (-not $found) {
      Write-Host "start.ps1 laeuft noch -- wird mitsamt seinem Prozessbaum beendet."
      $found = $true
    }
    Stop-ProcessTree $processId
    $script:stopped++
  }
}


# --- 2. whatever still sits on a port of the lab -----------------------------

function Stop-PortListener([string]$portValue, [string]$label) {
  if (-not $portValue) { return }
  $port = [int]$portValue
  $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
  if (-not $connections) { return }

  $ownedPids = $connections.OwningProcess | Select-Object -Unique
  $ours = @()
  foreach ($processId in $ownedPids) {
    $processId = [int]$processId
    if (Test-IsOwn $processId) { continue }
    if (Test-BelongsToLab $processId) {
      $ours += $processId
    } else {
      $name = (Get-Process -Id $processId -ErrorAction SilentlyContinue).ProcessName
      if (-not $name) { $name = 'unbekannt' }
      Write-Host "  $label ($port): der Port gehoert einem fremden Prozess ($name, $processId) -- bleibt unbehelligt."
    }
  }
  if ($ours.Count -eq 0) { return }

  foreach ($processId in $ours) {
    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
  }
  Start-Sleep -Seconds 1

  $remaining = $ours | Where-Object { Test-Alive $_ }
  if ($remaining) {
    $remainingText = $remaining -join ', '
    Write-Host "  $label ($port): laesst sich nicht beenden ($remainingText)."
    return
  }
  Write-Host "  $label ($port): gestoppt"
  $script:stopped++
}


# --- 3. what is left over and has no port any more ---------------------------

function Stop-StrayProcess {
  $candidates = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -match 'spring-boot:run|vite|start\.ps1' }
  if (-not $candidates) { return }

  $found = $false
  foreach ($candidate in $candidates) {
    $processId = [int]$candidate.ProcessId
    if (Test-IsOwn $processId) { continue }
    if (-not (Test-Alive $processId)) { continue }
    if (-not (Test-BelongsToLab $processId $candidate.CommandLine)) { continue }
    if (-not $found) {
      Write-Host "Rest ohne Port:"
      $found = $true
    }
    $command = $candidate.CommandLine
    if ($command.Length -gt 70) { $command = $command.Substring(0, 70) }
    Write-Host "  $processId  $command"
    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    Wait-Gone $processId 5 | Out-Null
    $script:stopped++
  }
}


if ($everything) {
  Write-Host "Beende das Lab."
  Stop-StartScript
} else {
  Write-Host "Beende: $($selected -join ' ')"
}

foreach ($name in $selected) {
  Stop-PortListener (Get-PortOf $name) $name
  Stop-PortListener (Get-DebugPortOf $name) "$name, Debug-Port"
}

if ($everything) { Stop-StrayProcess }

Write-Host ""
if ($stopped -eq 0) {
  Write-Host "Nichts zu beenden -- das Lab lief nicht."
} else {
  Write-Host "Fertig."
}
