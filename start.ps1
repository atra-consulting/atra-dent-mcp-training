#Requires -Version 5.1

# Windows equivalent of start.sh. Starts the lab locally.
#
# Architecture note: bash's "start_${name} &" forks a subshell that already
# has every variable and function of the parent in memory. PowerShell has no
# equivalent of fork() -- a background job or a directly-launched process is
# always a fresh process. So each component here is actually started by
# re-invoking THIS SAME SCRIPT in a hidden worker mode
# ("start.ps1 __worker__ <name> [--debug]"), which just calls the matching
# Start-<name> function and exits. That gives every component a real,
# trackable child process (for tree-killing and live output) without
# duplicating any of the component logic below.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

. "$root/components.ps1"
. "$root/wissen/stand.ps1"

$debug = $false
$selected = @()

function Write-ErrorAndExit([string]$message) {
  [Console]::Error.WriteLine("FEHLER: $message")
  exit 1
}

function Write-Warn([string]$message) {
  [Console]::Error.WriteLine("Hinweis: $message")
}

# $ErrorActionPreference does not reach a native command: mvn and npm report
# failure through their exit code and PowerShell carries on regardless. This is
# the "set -e" that the bash twin gets from its shebang line.
function Assert-LastExitCode([string]$what) {
  if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) {
    Write-ErrorAndExit "$what ist mit Exit-Code $LASTEXITCODE fehlgeschlagen."
  }
}

function Test-IsSelected([string]$candidate) {
  return $selected -contains $candidate
}

function Get-DebugProtocolOf([string]$name) {
  if (Test-RunsOnJvm $name) { return 'JDWP' } else { return 'Node-Inspector' }
}

# Empty when --debug is off. It replaces whatever a module declares as its own
# jvmArguments rather than adding to it -- the Wissensdienst therefore runs
# without --enable-native-access while a debugger is attached, and says so in
# three warnings. Nothing breaks by it. A <jvmArguments> in a plugin
# configuration would be worse: it would win over this option, and the agent
# would go missing with nothing to show for it.
function Get-JvmDebugOption([string]$name) {
  if (-not $debug) { return $null }
  return "-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${DebugHost}:$(Get-DebugPortOf $name)"
}

function Get-HealthOf([string]$name) {
  $port = Get-PortOf $name
  if ($name -in @('rechenkern', 'kernsystem', 'wissen', 'agenten-orchestrator', 'agenten-beratung', 'agenten-schadensfall')) {
    return "http://localhost:$port/actuator/health"
  }
  return "http://localhost:$port/"
}

function Get-DescriptionOf([string]$name) {
  switch ($name) {
    'rechenkern' { 'Rechenkern -- REST und MCP' }
    'kernsystem' { 'Kernsystem -- REST und MCP' }
    'wissen' { 'Wissensdienst -- REST und MCP' }
    'agenten-orchestrator' { 'Orchestrator -- A2A, ordnet ein und reicht weiter' }
    'agenten-beratung' { 'Beratungsagent -- A2A nach außen, MCP nach innen' }
    'agenten-schadensfall' { 'Schadensfallagent -- Poller und A2A, prüft eingereichte Fälle' }
    'sachbearbeiter-ui' { 'Backoffice-Oberfläche' }
    'kunden-chat' { 'Chat-Oberfläche' }
  }
}

function Get-AddressesOf([string]$name) {
  $port = Get-PortOf $name
  $result = New-Object System.Collections.Generic.List[object]
  switch ($name) {
    { $_ -in @('rechenkern', 'kernsystem', 'wissen') } {
      $result.Add([PSCustomObject]@{ Label = 'REST'; Address = "http://localhost:$port/api/v1" })
      $result.Add([PSCustomObject]@{ Label = 'MCP'; Address = "http://localhost:$port/mcp" })
    }
    { $_ -in @('agenten-orchestrator', 'agenten-beratung', 'agenten-schadensfall') } {
      $result.Add([PSCustomObject]@{ Label = 'Agent Card'; Address = "http://localhost:$port/.well-known/agent-card.json" })
      $result.Add([PSCustomObject]@{ Label = 'JSON-RPC'; Address = "http://localhost:$port/  (POST, Methode message/send)" })
    }
    default {
      $result.Add([PSCustomObject]@{ Label = 'Oberfläche'; Address = "http://localhost:$port" })
    }
  }
  if ($debug) {
    $result.Add([PSCustomObject]@{ Label = 'Debugger'; Address = "${DebugHost}:$(Get-DebugPortOf $name)  ($(Get-DebugProtocolOf $name))" })
  }
  return $result
}


function Test-ToolOrExit([string]$name, [string]$hint) {
  if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
    Write-ErrorAndExit "'$name' wurde nicht gefunden. $hint"
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

function Assert-Java {
  Test-ToolOrExit 'java' 'Die Dienste brauchen Java 25 (LTS), z. B. Eclipse Temurin: https://adoptium.net.'
  Test-ToolOrExit 'mvn' 'Die Dienste werden mit Maven gebaut und gestartet: https://maven.apache.org (Windows: es gibt kein winget-Paket -- Binary-ZIP von https://maven.apache.org/download.cgi entpacken und dessen bin-Verzeichnis in den PATH aufnehmen).'

  $versionOutput = Get-JavaVersionOutput
  $majorVersion = $null
  if ($versionOutput -match 'version "(\d+)') { $majorVersion = [int]$Matches[1] }
  if (-not $majorVersion -or $majorVersion -lt 25) {
    $shown = if ($majorVersion) { $majorVersion } else { 'unbekannter Version' }
    Write-ErrorAndExit "Java $shown ist zu alt. Die Dienste verlangen Java 25."
  }
}

function Assert-Node {
  Test-ToolOrExit 'node' 'Die Oberflächen brauchen Node.js 20.19+ (oder 22.12+): https://nodejs.org (Windows: winget install OpenJS.NodeJS.LTS).'
  Test-ToolOrExit 'npm' 'npm gehört zu Node.js; eine unvollständige Installation bitte erneuern.'

  $version = (& node --version).TrimStart('v')
  $parts = $version.Split('.')
  $major = [int]$parts[0]
  $minor = [int]$parts[1]
  if ($major -lt 20 -or ($major -eq 20 -and $minor -lt 19)) {
    Write-ErrorAndExit "Node.js $version ist zu alt. Vite 8 verlangt 20.19+ oder 22.12+."
  }
}

function Assert-Gemini([string]$service) {
  if (-not $env:GEMINI_API_KEY -and -not $env:GOOGLE_API_KEY) {
    Write-ErrorAndExit "GEMINI_API_KEY fehlt, und $service startet ohne ihn nicht. Lege .env an (Vorlage: .env.beispiel) oder setze die Variable in der Umgebung."
  }
}

function Test-Reachable([string]$url) {
  try {
    Invoke-WebRequest -Uri $url -UseBasicParsing -Method Get -TimeoutSec 5 -ErrorAction Stop | Out-Null
    return $true
  } catch {
    return $false
  }
}

function Install-DependenciesIfMissing([string]$directory) {
  $modules = Join-Path $directory 'node_modules'
  $viteBin = Join-Path $modules '.bin/vite.cmd'
  if (-not (Test-Path -LiteralPath $modules) -or -not (Test-Path -LiteralPath $viteBin)) {
    Write-Host "${directory}: Abhängigkeiten fehlen oder sind unvollständig, npm install läuft."
    & npm --prefix $directory install
    Assert-LastExitCode "npm install in $directory"
  }
}

# npm would hand NODE_OPTIONS to its own node as well as to vite's, and the
# second one then fails to bind the inspector. Calling vite directly skips the
# wrapper and leaves exactly one inspector.
function Start-Vite([string]$name, [string]$port) {
  if (-not $debug) {
    & npm --prefix $name run dev -- --port $port --strictPort
    return
  }
  Set-Location (Join-Path $root $name)
  & node "--inspect=${DebugHost}:$(Get-DebugPortOf $name)" ./node_modules/vite/bin/vite.js dev --port $port --strictPort
}

function Assert-ViteEntry([string]$directory) {
  if (-not $debug) { return }
  $entry = Join-Path $directory 'node_modules/vite/bin/vite.js'
  if (-not (Test-Path -LiteralPath $entry)) {
    Write-ErrorAndExit "${directory}: node_modules/vite/bin/vite.js fehlt -- ohne die Datei laesst sich der Node-Inspector nicht anhaengen. 'npm --prefix ${directory} install' holt sie."
  }
}


function Assert-PortFree([string]$portValue, [string]$name) {
  $port = [int]$portValue
  $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
  if ($connections) {
    Write-ErrorAndExit "Port $port ist belegt ($name). Läuft das Lab noch aus einem anderen Terminal?"
  }
}

# Only what this repository owns. The port was free when the lab started, so a
# listener on it now is almost always the building block that would not let go
# -- but "almost always" is not a licence to shoot: between the failed start of
# a service and Ctrl+C, something else may well have taken the port, and that
# something else is none of this script's business.
function Clear-Port([string]$portValue) {
  $port = [int]$portValue
  $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
  if (-not $connections) { return }
  $pidsOnPort = $connections.OwningProcess | Select-Object -Unique
  foreach ($processId in $pidsOnPort) {
    if (-not (Test-BelongsToLab ([int]$processId))) { continue }
    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
  }
}

function Wait-ForReady([string]$name, [System.Diagnostics.Process]$process) {
  $address = Get-HealthOf $name
  $seconds = 0
  $limit = 180
  while ($seconds -lt $limit) {
    if (Test-Reachable $address) { return $true }
    if ($process.HasExited) {
      Write-Warn "$name hat sich beim Start beendet -- der Grund steht in der Ausgabe oben."
      return $false
    }
    Start-Sleep -Seconds 1
    $seconds++
  }
  Write-Warn "$name war nach $limit s noch nicht bereit -- die Ausgabe oben sagt, woran es liegt."
  return $false
}


# --- rechenkern ---------------------------------------------------------------

function Initialize-rechenkern { Assert-Java }

function Start-rechenkern {
  & mvn -B -q -f produktmodell/pom.xml install -DskipTests
  Assert-LastExitCode 'mvn install von produktmodell'

  $env:SERVER_PORT = $RechenkernPort
  $mvnArgs = @('-q', '-f', 'rechenkern/pom.xml', 'spring-boot:run')
  $jvmOption = Get-JvmDebugOption 'rechenkern'
  if ($jvmOption) { $mvnArgs += $jvmOption }
  & mvn @mvnArgs
}

# --- kernsystem ----------------------------------------------------------------

$KernsystemTemplate = Join-Path $root 'kernsystem/data'
$KernsystemRuntime = Join-Path $root 'kernsystem/runtime'
$resetStore = $false

function Initialize-Store {
  $action = $null
  if ($resetStore) {
    Remove-Item -LiteralPath $KernsystemRuntime -Recurse -Force -ErrorAction SilentlyContinue
    $action = 'zurueckgesetzt'
  } elseif (Test-Path -LiteralPath $KernsystemRuntime) {
    # Der Bestand ist eine Kopie der Vorlage und altert mit ihr. Aendert sich
    # die Vorlage -- neue Faelle, oder ein Statuswert, den der Code nicht mehr
    # kennt --, passt die Kopie nicht mehr zum Dienst. Ohne diesen Vergleich
    # faellt das erst beim Start auf, als Stacktrace ohne Hinweis.
    $seedFile = Join-Path $KernsystemRuntime '.seed'
    $recorded = 'unbekannt'
    if (Test-Path -LiteralPath $seedFile) { $recorded = (Get-Content -LiteralPath $seedFile -Raw).Trim() }
    $current = Get-FingerprintOf -Paths @($KernsystemTemplate)
    if ($recorded -ne $current) {
      Write-Warn "kernsystem/data/ hat sich geaendert, seit kernsystem/runtime/ daraus angelegt wurde.`n       Der Bestand kann Werte enthalten, die der Dienst nicht mehr liest.`n       Zuruecksetzen mit: .\start.ps1 --bestand-zuruecksetzen"
    }
    return
  } else {
    $action = 'angelegt'
  }

  if (-not (Test-Path -LiteralPath $KernsystemTemplate)) {
    Write-ErrorAndExit "Die Vorlage kernsystem/data/ fehlt -- ohne sie gibt es keinen Bestand zum Kopieren."
  }

  New-Item -ItemType Directory -Force -Path $KernsystemRuntime | Out-Null
  Copy-Item -Path (Join-Path $KernsystemTemplate '*') -Destination $KernsystemRuntime -Recurse -Force
  Get-FingerprintOf -Paths @($KernsystemTemplate) | Set-Content -LiteralPath (Join-Path $KernsystemRuntime '.seed') -NoNewline
  Write-Host "  kernsystem/runtime aus der Vorlage kernsystem/data $action"
}

function Initialize-kernsystem {
  Assert-Java
  Initialize-Store
}

function Start-kernsystem {
  $env:SERVER_PORT = $KernsystemPort
  $env:KERNSYSTEM_DATA_DIRECTORY = $KernsystemRuntime
  $mvnArgs = @('-q', '-f', 'kernsystem/pom.xml', 'spring-boot:run')
  $jvmOption = Get-JvmDebugOption 'kernsystem'
  if ($jvmOption) { $mvnArgs += $jvmOption }
  & mvn @mvnArgs
}

# --- wissen ----------------------------------------------------------------

function Initialize-wissen {
  Assert-Java

  if (-not (Test-Path -LiteralPath 'wissen/generated/stand.txt')) {
    Write-Warn "wissen/generated/stand.txt fehlt -- .\wissen\build.ps1 erzeugt Dokumente, Indizes und Stand neu."
  } elseif ((Get-ComputeStatus) -ne (Get-Content -LiteralPath 'wissen/generated/stand.txt' -Raw).Trim()) {
    Write-Warn "Die Quellen unter wissen/ passen nicht mehr zu den erzeugten Dokumenten -- .\wissen\build.ps1 ausführen."
  }

  $env:SPRING_AI_EMBEDDING_TRANSFORMER_CACHE_DIRECTORY = Join-Path $root 'wissen/modell'
}

function Start-wissen {
  & mvn -B -q -f produktmodell/pom.xml install -DskipTests
  Assert-LastExitCode 'mvn install von produktmodell'

  $env:SERVER_PORT = $WissenPort
  $mvnArgs = @('-q', '-f', 'wissen/dienst/pom.xml', 'spring-boot:run')
  $jvmOption = Get-JvmDebugOption 'wissen'
  if ($jvmOption) { $mvnArgs += $jvmOption }
  & mvn @mvnArgs
}

# --- agenten (shared) --------------------------------------------------------

$agentsBuilt = $false

function Build-Agents {
  if ($agentsBuilt) { return }
  $script:agentsBuilt = $true
  Write-Host "agenten: mvn install im Reaktor (einmal je Lauf, ohne Tests)."
  & mvn -B -q -f agenten/pom.xml install -DskipTests
  Assert-LastExitCode 'mvn install im Reaktor agenten'
}

function Wait-ForNeighbour([string]$caller, [string]$component, [string]$port) {
  if (-not (Test-IsSelected $component)) { return }
  if (Test-Reachable "http://localhost:$port/actuator/health") { return }
  Write-Host "${caller}: warte auf ${component} (Port ${port})."
  $seconds = 0
  $limit = 300
  while (-not (Test-Reachable "http://localhost:$port/actuator/health")) {
    if ($seconds -ge $limit) {
      Write-Warn "${caller}: ${component} war nach ${limit} s nicht bereit. Der Start läuft weiter -- die Ausgabe oben sagt, woran es liegt."
      return
    }
    Start-Sleep -Seconds 1
    $seconds++
  }
}

# --- agenten-beratung --------------------------------------------------------

function Initialize-agenten-beratung {
  Assert-Java
  Assert-Gemini 'der Beratungsagent'

  $missing = @()
  if (-not (Test-IsSelected 'kernsystem') -and -not (Test-Reachable "http://localhost:$KernsystemPort/actuator/health")) { $missing += "Kernsystem ($KernsystemPort)" }
  if (-not (Test-IsSelected 'rechenkern') -and -not (Test-Reachable "http://localhost:$RechenkernPort/actuator/health")) { $missing += "Rechenkern ($RechenkernPort)" }
  if (-not (Test-IsSelected 'wissen') -and -not (Test-Reachable "http://localhost:$WissenPort/actuator/health")) { $missing += "Wissensdienst ($WissenPort)" }

  if ($missing.Count -gt 0) {
    Write-Warn "agenten-beratung braucht Kernsystem, Rechenkern und Wissensdienst als MCP-Server; nicht erreichbar und nicht mitgestartet: $($missing -join ', '). Der Dienst wird beim Verbindungsaufbau abbrechen. Vollständig:  .\start.ps1 rechenkern kernsystem wissen agenten-beratung agenten-orchestrator"
  }

  Build-Agents
}

function Start-agenten-beratung {
  Wait-ForNeighbour 'agenten-beratung' 'kernsystem' $KernsystemPort
  Wait-ForNeighbour 'agenten-beratung' 'rechenkern' $RechenkernPort
  Wait-ForNeighbour 'agenten-beratung' 'wissen' $WissenPort

  $env:AGENTEN_BERATUNG_PORT = $AgentenBeratungPort
  if (-not $env:AGENTEN_CARD) { $env:AGENTEN_CARD = Join-Path $root 'agenten/cards/beratung.yaml' }
  if (-not $env:AGENTEN_BERATUNG_BASE_URL) { $env:AGENTEN_BERATUNG_BASE_URL = "http://localhost:$AgentenBeratungPort" }
  if (-not $env:AGENTEN_MCP_KERNSYSTEM_URL) { $env:AGENTEN_MCP_KERNSYSTEM_URL = "http://localhost:$KernsystemPort" }
  if (-not $env:AGENTEN_MCP_RECHENKERN_URL) { $env:AGENTEN_MCP_RECHENKERN_URL = "http://localhost:$RechenkernPort" }
  if (-not $env:AGENTEN_MCP_WISSEN_URL) { $env:AGENTEN_MCP_WISSEN_URL = "http://localhost:$WissenPort" }

  $mvnArgs = @('-q', '-f', 'agenten/beratung/pom.xml', 'spring-boot:run')
  $jvmOption = Get-JvmDebugOption 'agenten-beratung'
  if ($jvmOption) { $mvnArgs += $jvmOption }
  & mvn @mvnArgs
}

# --- agenten-schadensfall ----------------------------------------------------

function Initialize-agenten-schadensfall {
  Assert-Java
  Assert-Gemini 'der Schadensfallagent'

  $missing = @()
  if (-not (Test-IsSelected 'kernsystem') -and -not (Test-Reachable "http://localhost:$KernsystemPort/actuator/health")) { $missing += "Kernsystem ($KernsystemPort)" }
  if (-not (Test-IsSelected 'rechenkern') -and -not (Test-Reachable "http://localhost:$RechenkernPort/actuator/health")) { $missing += "Rechenkern ($RechenkernPort)" }
  if (-not (Test-IsSelected 'wissen') -and -not (Test-Reachable "http://localhost:$WissenPort/actuator/health")) { $missing += "Wissensdienst ($WissenPort)" }

  if ($missing.Count -gt 0) {
    Write-Warn "agenten-schadensfall braucht Kernsystem, Rechenkern und Wissensdienst als MCP-Server; nicht erreichbar und nicht mitgestartet: $($missing -join ', '). Der Dienst wird beim Verbindungsaufbau abbrechen. Vollständig:  .\start.ps1 rechenkern kernsystem wissen agenten-schadensfall"
  }

  Build-Agents
}

function Start-agenten-schadensfall {
  Wait-ForNeighbour 'agenten-schadensfall' 'kernsystem' $KernsystemPort
  Wait-ForNeighbour 'agenten-schadensfall' 'rechenkern' $RechenkernPort
  Wait-ForNeighbour 'agenten-schadensfall' 'wissen' $WissenPort

  $env:AGENTEN_SCHADENSFALL_PORT = $AgentenSchadensfallPort
  if (-not $env:AGENTEN_CARD) { $env:AGENTEN_CARD = Join-Path $root 'agenten/cards/schadensfall.yaml' }
  if (-not $env:AGENTEN_SCHADENSFALL_BASE_URL) { $env:AGENTEN_SCHADENSFALL_BASE_URL = "http://localhost:$AgentenSchadensfallPort" }
  if (-not $env:AGENTEN_MCP_KERNSYSTEM_URL) { $env:AGENTEN_MCP_KERNSYSTEM_URL = "http://localhost:$KernsystemPort" }
  if (-not $env:AGENTEN_MCP_RECHENKERN_URL) { $env:AGENTEN_MCP_RECHENKERN_URL = "http://localhost:$RechenkernPort" }
  if (-not $env:AGENTEN_MCP_WISSEN_URL) { $env:AGENTEN_MCP_WISSEN_URL = "http://localhost:$WissenPort" }
  if (-not $env:KERNSYSTEM_REST_URL) { $env:KERNSYSTEM_REST_URL = "http://localhost:$KernsystemPort" }

  $mvnArgs = @('-q', '-f', 'agenten/schadensfall/pom.xml', 'spring-boot:run')
  $jvmOption = Get-JvmDebugOption 'agenten-schadensfall'
  if ($jvmOption) { $mvnArgs += $jvmOption }
  & mvn @mvnArgs
}

# --- agenten-orchestrator ----------------------------------------------------

function Initialize-agenten-orchestrator {
  Assert-Java
  Assert-Gemini 'der Orchestrator'

  if (-not (Test-IsSelected 'kernsystem') -and -not (Test-Reachable "http://localhost:$KernsystemPort/actuator/health")) {
    Write-Warn "agenten-orchestrator schlägt die Kundendaten über MCP im Kernsystem ($KernsystemPort) nach; nicht erreichbar und nicht mitgestartet. Der Dienst wird beim Verbindungsaufbau abbrechen. Vollständig:  .\start.ps1 kernsystem agenten-orchestrator"
  }

  if (-not (Test-IsSelected 'agenten-beratung') -and -not (Test-Reachable "http://localhost:$AgentenBeratungPort/actuator/health")) {
    Write-Warn "agenten-orchestrator läuft ohne agenten-beratung ($AgentenBeratungPort), beantwortet dann aber jede Beratungsfrage mit einer Absage."
  }

  if (-not (Test-IsSelected 'agenten-schadensfall') -and -not (Test-Reachable "http://localhost:$AgentenSchadensfallPort/actuator/health")) {
    Write-Warn "agenten-orchestrator läuft ohne agenten-schadensfall ($AgentenSchadensfallPort); er entdeckt dann nur den Beratungsagenten."
  }

  Build-Agents
}

function Start-agenten-orchestrator {
  Wait-ForNeighbour 'agenten-orchestrator' 'kernsystem' $KernsystemPort
  Wait-ForNeighbour 'agenten-orchestrator' 'agenten-beratung' $AgentenBeratungPort
  Wait-ForNeighbour 'agenten-orchestrator' 'agenten-schadensfall' $AgentenSchadensfallPort

  $env:AGENTEN_ORCHESTRATOR_PORT = $AgentenOrchestratorPort
  if (-not $env:AGENTEN_CARD) { $env:AGENTEN_CARD = Join-Path $root 'agenten/cards/orchestrator.yaml' }
  if (-not $env:AGENTEN_ORCHESTRATOR_BASE_URL) { $env:AGENTEN_ORCHESTRATOR_BASE_URL = "http://localhost:$AgentenOrchestratorPort" }
  if (-not $env:AGENTEN_MCP_KERNSYSTEM_URL) { $env:AGENTEN_MCP_KERNSYSTEM_URL = "http://localhost:$KernsystemPort" }
  if (-not $env:AGENTEN_BERATUNG_URL) { $env:AGENTEN_BERATUNG_URL = "http://localhost:$AgentenBeratungPort" }
  if (-not $env:AGENTEN_SCHADENSFALL_URL) { $env:AGENTEN_SCHADENSFALL_URL = "http://localhost:$AgentenSchadensfallPort" }

  $mvnArgs = @('-q', '-f', 'agenten/orchestrator/pom.xml', 'spring-boot:run')
  $jvmOption = Get-JvmDebugOption 'agenten-orchestrator'
  if ($jvmOption) { $mvnArgs += $jvmOption }
  & mvn @mvnArgs
}

# --- sachbearbeiter-ui --------------------------------------------------------

function Initialize-sachbearbeiter-ui {
  Assert-Node
  Install-DependenciesIfMissing 'sachbearbeiter-ui'
  Assert-ViteEntry 'sachbearbeiter-ui'
}

function Start-sachbearbeiter-ui {
  if (-not $env:SCHADENSFALLAGENT_URL) { $env:SCHADENSFALLAGENT_URL = "http://localhost:$AgentenSchadensfallPort" }
  Start-Vite 'sachbearbeiter-ui' $SachbearbeiterUiPort
}

# --- kunden-chat --------------------------------------------------------------

function Initialize-kunden-chat {
  Assert-Node
  Install-DependenciesIfMissing 'kunden-chat'
  Assert-ViteEntry 'kunden-chat'

  $client = if ($env:AGENT_CLIENT) { $env:AGENT_CLIENT } else { 'a2a' }
  if ($client -ne 'a2a') { return }
  if (-not (Test-IsSelected 'agenten-orchestrator') -and -not (Test-Reachable "http://localhost:$AgentenOrchestratorPort/actuator/health")) {
    Write-Warn "kunden-chat spricht den Orchestrator ($AgentenOrchestratorPort) an; der läuft nicht und ist nicht mitgestartet. Jede Nachricht bekommt dann eine Absage. Vollständig:  .\start.ps1 rechenkern kernsystem wissen agenten-beratung agenten-orchestrator kunden-chat   -- oder ohne Java:  `$env:AGENT_CLIENT='mock'; .\start.ps1 kunden-chat"
  }
}

function Start-kunden-chat {
  if (-not $env:AGENT_URL) { $env:AGENT_URL = "http://localhost:$AgentenOrchestratorPort/" }
  if (-not $env:WISSEN_URL) { $env:WISSEN_URL = "http://localhost:$WissenPort/api/v1" }
  if (-not $env:BODY_SIZE_LIMIT) { $env:BODY_SIZE_LIMIT = '10M' }
  Start-Vite 'kunden-chat' $KundenChatPort
}


# --- process orchestration ----------------------------------------------------

$Colors = @('Cyan', 'Yellow', 'Magenta', 'Green')
$tracked = New-Object System.Collections.Generic.List[object]
$eventSubscriptions = New-Object System.Collections.Generic.List[int]

# ProcessStartInfo.ArgumentList, which would do this on its own, arrived with
# .NET Core 2.1 and never reached .NET Framework -- so under Windows
# PowerShell 5.1 the property is simply absent and the command line has to be
# assembled here. The rule is the one CommandLineToArgvW documents: a run of
# backslashes in front of a quote is doubled, so is a trailing run, and the
# whole argument is wrapped whenever it carries a space or a quote. Only the
# path to this script can contain either, but a repository under
# "C:\Users\Vor Nachname\" is not an exotic setup.
function Format-CommandLineArgument([string]$value) {
  if ($value.Length -gt 0 -and $value -notmatch '[\s"]') { return $value }
  $escaped = $value -replace '(\\*)"', '$1$1\"'
  $escaped = $escaped -replace '(\\+)$', '$1$1'
  return '"' + $escaped + '"'
}

function Start-ComponentProcess {
  param([string]$Name, [bool]$Single, [int]$ColorIndex)

  $hostPath = (Get-Process -Id $PID).Path

  $arguments = @('-NoProfile', '-NonInteractive', '-File', (Join-Path $root 'start.ps1'), '__worker__', $Name)
  if ($debug) { $arguments += '--debug' }

  $psi = New-Object System.Diagnostics.ProcessStartInfo
  $psi.FileName = $hostPath
  $psi.WorkingDirectory = $root
  $psi.UseShellExecute = $false
  $psi.CreateNoWindow = $true
  $psi.Arguments = (@($arguments | ForEach-Object { Format-CommandLineArgument $_ }) -join ' ')

  if (-not $Single) {
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
  }

  $process = New-Object System.Diagnostics.Process
  $process.StartInfo = $psi
  $process.EnableRaisingEvents = $true

  if (-not $Single) {
    $color = $Colors[$ColorIndex % $Colors.Count]
    $label = '{0,-20}' -f $Name
    $messageData = [PSCustomObject]@{ Label = $label; Color = $color }
    $action = {
      if ($null -ne $EventArgs.Data) {
        $md = $Event.MessageData
        Write-Host ("{0} |" -f $md.Label) -NoNewline -ForegroundColor $md.Color
        Write-Host " $($EventArgs.Data)"
      }
    }
    $subOut = Register-ObjectEvent -InputObject $process -EventName OutputDataReceived -MessageData $messageData -Action $action
    $subErr = Register-ObjectEvent -InputObject $process -EventName ErrorDataReceived -MessageData $messageData -Action $action
    $eventSubscriptions.Add($subOut.Id)
    $eventSubscriptions.Add($subErr.Id)
  }

  $process.Start() | Out-Null
  if (-not $Single) {
    $process.BeginOutputReadLine()
    $process.BeginErrorReadLine()
  }
  return $process
}

function Invoke-Cleanup {
  if ($tracked.Count -eq 0) { return }
  Write-Host ""
  Write-Host "Beende das Lab."
  foreach ($entry in $tracked) {
    $wasRunning = -not $entry.Process.HasExited
    Stop-ProcessTree $entry.Process.Id
    Clear-Port (Get-PortOf $entry.Name)
    if ($wasRunning) { Write-Host "  $($entry.Name) gestoppt" }
  }
  foreach ($subId in $eventSubscriptions) {
    Unregister-Event -SubscriptionId $subId -ErrorAction SilentlyContinue
  }
}


function Show-Help {
  @"
Startet das Lab lokal.

  .\start.ps1                          alle Bausteine
  .\start.ps1 wissen agenten-beratung  nur die genannten
  .\start.ps1 --tracelog               vollständiges Tracelog schreiben
  .\start.ps1 --debug                  Debug-Ports öffnen, IDE hängt sich an
  .\start.ps1 --bestand-zuruecksetzen  Kundendaten und Fälle aus der Vorlage neu
  .\start.ps1 --hilfe                  diese Übersicht

Bausteine und Standardports:
  rechenkern              8086   Rechenkern -- REST und MCP
  kernsystem              8080   Kernsystem -- REST und MCP
  wissen                  8082   Wissensdienst -- REST und MCP
  agenten-beratung        8085   Beratungsagent -- A2A außen, MCP innen
  agenten-schadensfall    8087   Schadensfallagent -- prüft eingereichte Fälle
  agenten-orchestrator    8084   Orchestrator -- ordnet ein und reicht weiter
  sachbearbeiter-ui       5173   Backoffice-Oberfläche
  kunden-chat             5174   Chat-Oberfläche

Jeder Port lässt sich über die Umgebung überschreiben:
  RECHENKERN_PORT  KERNSYSTEM_PORT  WISSEN_PORT
  AGENTEN_ORCHESTRATOR_PORT  AGENTEN_BERATUNG_PORT  AGENTEN_SCHADENSFALL_PORT
  SACHBEARBEITER_UI_PORT  KUNDEN_CHAT_PORT

  `$env:WISSEN_PORT = 9000; .\start.ps1 wissen

8080 gehoert dem Kernsystem, so wie es in oas/openapi.yaml steht. Das Formular
des Rechnungsgenerators will denselben Port und wird von diesem Skript nicht
gestartet -- wer beides braucht:

  `$env:KERNSYSTEM_PORT = 8083; .\start.ps1

8083 ist deshalb keinem Baustein zugeteilt; die Agenten liegen auf 8084/8085
und 8087, der Rechenkern auf 8086. 8081 ist seit dem Umzug des Arztservice
ebenfalls frei -- der Dienst wird fremd gehostet und hat keinen Port mehr,
sondern eine Adresse.

Fremde Agenten sind keine Bausteine dieses Skripts:
  Welche Agenten ein Agent kennt, steht in seiner application.yaml unter
  agenten.subagents.catalog -- ein Eintrag je Agent, mit seiner url und,
  wenn seine Karte einen verlangt, seinem api-key. Ein Eintrag mehr oder
  weniger ist die ganze Verdrahtung. Dieses Skript startet sie nicht und
  prüft sie nicht; entdeckt werden sie über ihre Agent Card.

    AGENTEN_ARZTSERVICE_URL      Adresse des Arztservice; der Eintrag
                                 arztservice bleibt ohne sie leer und wird
                                 übersprungen
    AGENTEN_ARZTSERVICE_API_KEY  sein Schlüssel. In welchem Header er steht,
                                 sagt seine Karte -- nicht dieses Skript

  Fehlt der Schlüssel, steht der Agent trotzdem im Katalog (seine Karte ist
  ohne Schlüssel lesbar), aber jeder Aufruf wird abgewiesen. Ein Schlüssel
  gilt nur für den Eintrag, in dem er steht: kein anderer Agent bekommt ihn.

Wer wen braucht:
  agenten-*                   GEMINI_API_KEY aus .env, sonst kein Start
  kernsystem                  den Rechenkern fuer ein einzelnes Werkzeug
                              (mein_beitrag_berechnen) -- traege Abhaengigkeit,
                              alles andere laeuft auch ohne ihn. Kein Warten
                              beim Start, deshalb steht rechenkern vor
                              kernsystem in COMPONENTS
  agenten-beratung            Kernsystem, Rechenkern und Wissensdienst als
                              MCP-Server; werden sie mitgestartet, wartet er
                              auf sie
  agenten-schadensfall        dieselben drei MCP-Server -- ohne sie startet er
                              nicht. Dazu das Kernsystem ein zweites Mal ueber
                              REST (Holen, Uebernehmen, Zurueckrollen). Fuer
                              die Fachauskunft sucht er unter seinen
                              agenten.subagents.catalog einen Agenten mit der
                              Fertigkeit rechnung-beurteilen; findet er keinen,
                              endet jede Pruefung mit ARZT_NICHT_VERFUEGBAR
  agenten-orchestrator         den Beratungsagenten fuer die Fachauskunft und
                              den Schadensfallagenten fuer die Fallpruefung;
                              werden sie mitgestartet, wartet der Orchestrator
                              beim Start auf sie, damit die Discovery gleich
                              gelingt -- fehlt einer dauerhaft, laedt der
                              Orchestrator die Karte spaeter selbst nach
  kunden-chat                  den Orchestrator -- zum Antworten. Ohne Java:
                              `$env:AGENT_CLIENT = 'mock'; .\start.ps1 kunden-chat

Der Schadensfallagent faengt von sich aus an:
  Er sieht alle 10 s nach Faellen im Status "eingereicht", uebernimmt jeden
  davon und prueft ihn. Fuer eine Vorfuehrung, in der nichts von selbst
  passieren soll, laesst sich der Takt abschalten -- er prueft dann nur noch
  auf Anforderung ueber den A2A-Skill fall_pruefen:

    `$env:SCHADENSFALL_POLL_ENABLED = 'false'; .\start.ps1

  fall_pruefen verlangt dabei den Header x-kunden-id mit der Kundennummer der
  Fragenden -- ohne ihn gibt es zu keinem Fall eine Auskunft, auch nicht
  darueber, ob es ihn gibt. Der Takt oben braucht ihn nicht: Der Poller ruft
  die Pruefung unmittelbar und nicht ueber A2A.

  Weitere Stellschrauben: SCHADENSFALL_POLL_INTERVAL (10s),
  SCHADENSFALL_RECONCILE_TIMEOUT (20m), SCHADENSFALL_FREIGABE_THRESHOLD (200.00).

  Die Freigabeschwelle ist eine Grenze der Tragweite: Darueber empfiehlt der
  Agent auch bei sonst gruener Pruefung keine Freigabe mehr. Sie misst den
  Betrag des SCHADENSFALLS und nicht den der Rechnung -- eingereicht werden
  nur Positionen mit Gebuehrennummer, Material und Labor bleiben draussen.
  Mit 200,00 liegt etwa die Haelfte der Demorechnungen darunter.

  Die Reconcile-Frist holt einen Fall zurueck, der in "in_pruefung" haengen
  geblieben ist. Sie muss laenger sein als der laengstmoegliche Prueflauf --
  zwoelf Tool-Versuche, der langsamste (die A2A-Frage an den Arztservice) mit
  60 s Frist, dazu die Modellaufrufe dazwischen. Wer sie herunterdreht, laesst denselben Fall
  zweimal pruefen und erntet 409.

Debuggen (--debug):
  Öffnet zu jedem gestarteten Baustein einen Debug-Port und lässt ihn offen.
  Die sechs JVMs bekommen einen JDWP-Agenten, die beiden Oberflächen laufen
  unter dem Node-Inspector. Angehängt wird aus der IDE: Das Skript besitzt die
  Prozesse, die IDE besitzt die Verbindungen -- sie hängt sich an und wieder
  ab, ohne dass das Lab neu starten muss.

    .\start.ps1 --debug                  alle Bausteine mit Debug-Port
    .\start.ps1 --debug kernsystem       nur diesen einen

  Der Debug-Port ist der Port des Bausteins plus 1000:

    kernsystem              9080   JDWP
    wissen                  9082   JDWP
    agenten-orchestrator    9084   JDWP
    agenten-beratung        9085   JDWP
    rechenkern              9086   JDWP
    agenten-schadensfall    9087   JDWP
    sachbearbeiter-ui       6173   Node-Inspector
    kunden-chat             6174   Node-Inspector

  Jeder einzeln überschreibbar (KERNSYSTEM_DEBUG_PORT, WISSEN_DEBUG_PORT, ...),
  und DEBUG_HOST setzt die Adresse. Vorgabe ist 127.0.0.1 und nicht *: Ein
  offener JDWP-Port ist Codeausführung für jeden im selben Netz, und ein
  Workshop sitzt im selben WLAN.

  Fertige Run Configurations liegen im Repository -- IntelliJ findet sie unter
  .idea/runConfigurations/, VS Code unter .vscode/launch.json. "Debug: Alle"
  hängt sich an alle acht auf einmal, die einzelnen Einträge an je einen.

  Der Node-Inspector erreicht nur den Server-Teil der Oberflächen
  (+page.server.ts, +server.ts, hooks.server.ts, Laden beim SSR). Was im
  Browser läuft, braucht den Browser-Debugger und keinen Port von hier.

  Wer am Breakpoint länger steht als SCHADENSFALL_RECONCILE_TIMEOUT (20m),
  bekommt den Fall unter den Händen weggeholt. Für eine Sitzung am Breakpoint
  deshalb besser ohne Takt:

    `$env:SCHADENSFALL_POLL_ENABLED = 'false'; .\start.ps1 --debug agenten-schadensfall

Tracelog (--tracelog):
  Schreibt die vollständige Agenten- und MCP-Kommunikation nach tracelog/ --
  je Dienst eine Datei, eine Zeile JSON je Ereignis. Gedacht zum Nachsehen,
  wenn ein Agent etwas anderes getan hat als erwartet: Jeder A2A-Sprung, jeder
  Werkzeugaufruf samt Rohergebnis und jede Entscheidung im Modell stehen darin,
  mit Zeit und Gesprächskennung.

  Standardmäßig aus, und der Schalter gilt nur für diesen Start. Alles in
  zeitlicher Reihenfolge lesen:

    Get-Content tracelog\*.jsonl | jq -s 'sort_by(.timestamp)[]'

  Ein einzelnes Gespräch:

    Get-Content tracelog\*.jsonl | jq -s 'map(select(.gespraech=="<id>")) | sort_by(.timestamp)[]'

  Das Verzeichnis lässt sich umlenken:  `$env:AGENTEN_TRACELOG = 'C:\temp\lauf7'; .\start.ps1

  Das Tracelog enthält Klartext aus dem Gespräch samt Kundennummer und
  Rohergebnissen der Werkzeuge. tracelog/ ist deshalb nicht versioniert.

Bestand des Kernsystems (--bestand-zuruecksetzen):
  Kunden und Schadensfälle liegen zweimal. kernsystem/data/ ist die Vorlage:
  versioniert und zur Laufzeit unberührt. kernsystem/runtime/ ist der Stand
  dieses Rechners, nicht versioniert, und nur dorthin schreibt der Dienst.
  Eine Vorführung lässt den Arbeitsbaum damit sauber.

  Kopiert wird beim Start nur, wenn kernsystem/runtime/ noch fehlt. Gibt es
  das Verzeichnis, bleibt es unangetastet: Angelegte Kunden und geprüfte Fälle
  überdauern einen Neustart, so wie bisher. Zurück auf die Seed-Daten geht es
  nur auf Ansage:

    .\start.ps1 --bestand-zuruecksetzen

  Der Schalter greift beim Vorbereiten des Kernsystems. Wer ihn setzt, ohne
  das Kernsystem mitzustarten, bekommt einen Hinweis und sonst nichts.

Die Agentenmodule sind ein Maven-Reaktor; das Skript legt sie einmal je Lauf
mit "mvn install -DskipTests" ins lokale Repository, bevor ein Dienst startet.

Die erzeugten Dokumente und Suchindizes liegen fertig im Repository. Wer
Quellen unter wissen/ ändert, baut mit .\wissen\build.ps1 neu.

Geprüft wird nur, was gestartet wird. Beenden mit Strg+C; das Skript stoppt
alles, was es selbst gestartet hat. Wenn das einmal nicht reicht -- ein Fenster
weg, ein Dienst haengengeblieben --, raeumt .\stop.ps1 hinterher.
"@
}


# --- worker mode: run one component directly and exit ------------------------

if ($args.Count -ge 2 -and $args[0] -eq '__worker__') {
  $componentName = $args[1]
  $debug = $args -contains '--debug'
  $selectedRaw = $env:ATRA_LAB_SELECTED
  $selected = if ($selectedRaw) { $selectedRaw -split ',' } else { @() }

  & "Start-$componentName"
  exit $LASTEXITCODE
}


# --- normal orchestration mode ------------------------------------------------

$selectedList = New-Object System.Collections.Generic.List[string]
foreach ($arg in $args) {
  switch -Regex ($arg) {
    '^(-h|--hilfe|--help)$' { Show-Help; exit 0 }
    '^--tracelog$' {
      if (-not $env:AGENTEN_TRACELOG) { $env:AGENTEN_TRACELOG = Join-Path $root 'tracelog' }
    }
    '^--bestand-zuruecksetzen$' { $resetStore = $true }
    '^--debug$' { $debug = $true }
    default {
      if (-not (Test-IsComponent $arg)) {
        Write-ErrorAndExit "Unbekannter Baustein '$arg'. Möglich: $($Components -join ' ')"
      }
      if (-not $selectedList.Contains($arg)) { $selectedList.Add($arg) }
    }
  }
}

if ($selectedList.Count -eq 0) { $selectedList = [System.Collections.Generic.List[string]]::new([string[]]$Components) }
$selected = $selectedList

if ($resetStore -and -not (Test-IsSelected 'kernsystem')) {
  Write-Warn "--bestand-zuruecksetzen wirkt nur, wenn das Kernsystem mitstartet -- der Bestand bleibt unveraendert."
}

foreach ($name in $selected) {
  Assert-PortFree (Get-PortOf $name) $name
  if ($debug) { Assert-PortFree (Get-DebugPortOf $name) "$name, Debug-Port" }
}

Write-Host "Lab: $($selected -join ' ')"
if ($debug) {
  Write-Host "Debug: Ports offen, die IDE haengt sich an (Adressen stehen unten)"
}
if ($env:AGENTEN_TRACELOG) {
  Write-Host "Tracelog: $($env:AGENTEN_TRACELOG) (je Dienst eine .jsonl)"
}
Write-Host ""

foreach ($name in $selected) {
  & "Initialize-$name"
}
Write-Host ""

$env:ATRA_LAB_SELECTED = ($selected -join ',')

$single = ($selected.Count -eq 1)

try {
  $index = 0
  foreach ($name in $selected) {
    Write-Host "Starte $name ($(Get-DescriptionOf $name)) auf Port $(Get-PortOf $name)."
    $process = Start-ComponentProcess -Name $name -Single $single -ColorIndex $index
    $tracked.Add([PSCustomObject]@{ Name = $name; Process = $process })
    $index++
  }

  Write-Host ""
  Write-Host "Warte, bis alles bereit ist."
  if (Test-IsSelected 'wissen') {
    Write-Host "Beim ersten Start lädt der Wissensdienst sein Einbettungsmodell"
    Write-Host "(rund 490 MB) -- das dauert einmalig länger."
  }
  if (Test-IsSelected 'agenten-orchestrator') {
    Write-Host "Der Orchestrator entdeckt seine Subagenten beim Start per Agent Card;"
    Write-Host "die Zeile 'Agent entdeckt: ...' in seiner Ausgabe bestätigt das."
  }

  $ready = New-Object System.Collections.Generic.List[string]
  $failed = New-Object System.Collections.Generic.List[string]
  foreach ($entry in $tracked) {
    if (Wait-ForReady $entry.Name $entry.Process) {
      $ready.Add($entry.Name)
    } else {
      $failed.Add($entry.Name)
    }
  }

  Write-Host ""
  if ($ready.Count -gt 0) {
    Write-Host "Das Lab läuft:"
    foreach ($name in $ready) {
      Write-Host "  $name"
      foreach ($addr in (Get-AddressesOf $name)) {
        $padding = 12 - $addr.Label.Length
        if ($padding -lt 1) { $padding = 1 }
        Write-Host ("    {0}{1}{2}" -f $addr.Label, (' ' * $padding), $addr.Address)
      }
    }
  } else {
    Write-Host "Kein Baustein ist bereit geworden."
  }

  if ($failed.Count -gt 0) {
    Write-Host ""
    Write-Host "Nicht bereit: $($failed -join ' ')"
  }

  Write-Host ""
  Write-Host "Beenden mit Strg+C."
  Write-Host ""

  while ($true) {
    Start-Sleep -Milliseconds 500
    $anyRunning = $false
    foreach ($entry in $tracked) {
      if (-not $entry.Process.HasExited) { $anyRunning = $true; break }
    }
    if (-not $anyRunning) { break }
  }
} finally {
  Invoke-Cleanup
}
