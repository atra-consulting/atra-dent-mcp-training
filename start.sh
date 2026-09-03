#!/usr/bin/env bash

if [ -z "${BASH_VERSION:-}" ] || shopt -qo posix 2>/dev/null; then
  exec bash "$0" "$@"
fi

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$root"

# shellcheck source=components.sh
. ./components.sh
# shellcheck source=wissen/stand.sh
. ./wissen/stand.sh

debug=false

debug_protocol_of() {
  if runs_on_jvm "$1"; then echo "JDWP"; else echo "Node-Inspector"; fi
}

jvm_debug_option() {
  [ "$debug" = true ] || return 0
  printf -- '-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=%s:%s' \
    "$DEBUG_HOST" "$(debug_port_of "$1")"
}

health_of() {
  local port; port="$(port_of "$1")"
  case "$1" in
    rechenkern|kernsystem|wissen|agenten-orchestrator|agenten-beratung|agenten-schadensfall)
      echo "http://localhost:${port}/actuator/health" ;;
    *)
      echo "http://localhost:${port}/" ;;
  esac
}

description_of() {
  case "$1" in
    rechenkern)           echo "Rechenkern -- REST und MCP" ;;
    kernsystem)           echo "Kernsystem -- REST und MCP" ;;
    wissen)               echo "Wissensdienst -- REST und MCP" ;;
    agenten-orchestrator) echo "Orchestrator -- A2A, ordnet ein und reicht weiter" ;;
    agenten-beratung)     echo "Beratungsagent -- A2A nach außen, MCP nach innen" ;;
    agenten-schadensfall) echo "Schadensfallagent -- Poller und A2A, prüft eingereichte Fälle" ;;
    sachbearbeiter-ui)    echo "Backoffice-Oberfläche" ;;
    kunden-chat)          echo "Chat-Oberfläche" ;;
  esac
}

addresses_of() {
  local port; port="$(port_of "$1")"
  case "$1" in
    rechenkern|kernsystem|wissen)
      printf 'REST\thttp://localhost:%s/api/v1\n' "$port"
      printf 'MCP\thttp://localhost:%s/mcp\n' "$port"
      ;;
    agenten-orchestrator|agenten-beratung|agenten-schadensfall)
      printf 'Agent Card\thttp://localhost:%s/.well-known/agent-card.json\n' "$port"
      printf 'JSON-RPC\thttp://localhost:%s/  (POST, Methode message/send)\n' "$port"
      ;;
    *)
      printf 'Oberfläche\thttp://localhost:%s\n' "$port"
      ;;
  esac
  if [ "$debug" = true ]; then
    printf 'Debugger\t%s:%s  (%s)\n' \
      "$DEBUG_HOST" "$(debug_port_of "$1")" "$(debug_protocol_of "$1")"
  fi
}


error() {
  echo "FEHLER: $*" >&2
  exit 1
}

warn() {
  echo "Hinweis: $*" >&2
}

check_tool() {
  local name="$1" hint="$2"
  command -v "$name" >/dev/null 2>&1 \
    || error "'$name' wurde nicht gefunden. $hint"
}

check_java() {
  check_tool java \
    "Die Dienste brauchen Java 25 (LTS), z. B. Eclipse Temurin: https://adoptium.net."
  check_tool mvn \
    "Die Dienste werden mit Maven gebaut und gestartet: https://maven.apache.org (macOS: brew install maven)."

  local major_version
  major_version="$(java -version 2>&1 | awk -F'"' '/version/ { split($2, part, "."); print part[1]; exit }')"
  if [ "${major_version:-0}" -lt 25 ]; then
    error "Java ${major_version:-unbekannter Version} ist zu alt. Die Dienste verlangen Java 25."
  fi
}

check_node() {
  check_tool node \
    "Die Oberflächen brauchen Node.js 20.19+ (oder 22.12+): https://nodejs.org (macOS: brew install node)."
  check_tool npm \
    "npm gehört zu Node.js; eine unvollständige Installation bitte erneuern."

  local version major minor
  version="$(node --version | sed 's/^v//')"
  major="${version%%.*}"
  minor="$(echo "$version" | cut -d. -f2)"
  if [ "$major" -lt 20 ] || { [ "$major" -eq 20 ] && [ "$minor" -lt 19 ]; }; then
    error "Node.js ${version} ist zu alt. Vite 8 verlangt 20.19+ oder 22.12+."
  fi
}

check_gemini() {
  local service="$1"
  if [ -z "${GEMINI_API_KEY:-}" ] && [ -z "${GOOGLE_API_KEY:-}" ]; then
    error "GEMINI_API_KEY fehlt, und ${service} startet ohne ihn nicht. Lege .env an (Vorlage: .env.beispiel) oder setze die Variable in der Umgebung."
  fi
}

reachable() {
  command -v curl >/dev/null 2>&1 || return 0
  curl -fsS -o /dev/null "$1" 2>/dev/null
}

check_dependencies() {
  local directory="$1"
  if [ ! -d "$directory/node_modules" ] || [ ! -x "$directory/node_modules/.bin/vite" ]; then
    echo "${directory}: Abhängigkeiten fehlen oder sind unvollständig, npm install läuft."
    npm --prefix "$directory" install
  fi
}


run_vite() {
  local name="$1" port="$2"
  if [ "$debug" = false ]; then
    npm --prefix "$name" run dev -- --port "$port" --strictPort
    return
  fi
  cd "$root/$name"
  exec node "--inspect=${DEBUG_HOST}:$(debug_port_of "$name")" \
    ./node_modules/vite/bin/vite.js dev --port "$port" --strictPort
}

check_vite_entry() {
  [ "$debug" = true ] || return 0
  [ -f "$1/node_modules/vite/bin/vite.js" ] \
    || error "${1}: node_modules/vite/bin/vite.js fehlt -- ohne die Datei laesst sich der Node-Inspector nicht anhaengen. 'npm --prefix ${1} install' holt sie."
}


has_lsof=false
command -v lsof >/dev/null 2>&1 && has_lsof=true

check_port_free() {
  local port="$1" name="$2"
  [ "$has_lsof" = true ] || return 0
  if lsof -ti ":${port}" >/dev/null 2>&1; then
    error "Port ${port} ist belegt (${name}). Läuft das Lab noch aus einem anderen Terminal?"
  fi
}

free_port() {
  local port="$1" pids
  [ "$has_lsof" = true ] || return 0
  pids="$(lsof -ti ":${port}" 2>/dev/null || true)"
  [ -n "$pids" ] || return 0
  echo "$pids" | xargs kill 2>/dev/null || true
  sleep 1
  pids="$(lsof -ti ":${port}" 2>/dev/null || true)"
  [ -n "$pids" ] && echo "$pids" | xargs kill -9 2>/dev/null || true
  return 0
}

wait_for() {
  local name="$1" pid="$2" address seconds=0 limit=180
  address="$(health_of "$name")"
  command -v curl >/dev/null 2>&1 || return 0
  while [ "$seconds" -lt "$limit" ]; do
    if curl -fsS -o /dev/null "$address" 2>/dev/null; then
      return 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      warn "${name} hat sich beim Start beendet -- der Grund steht in der Ausgabe oben."
      return 1
    fi
    sleep 1
    seconds=$((seconds + 1))
  done
  warn "${name} war nach ${limit} s noch nicht bereit -- die Ausgabe oben sagt, woran es liegt."
  return 1
}


if [ -t 1 ]; then
  color_off=$'\033[0m'
  colors=($'\033[36m' $'\033[33m' $'\033[35m' $'\033[32m')
else
  color_off=''
  colors=('' '' '' '')
fi

with_prefix() {
  local label="$1" color="$2" line
  while IFS= read -r line; do
    printf '%s%-20s |%s %s\n' "$color" "$label" "$color_off" "$line"
  done
}


prepare_rechenkern() {
  check_java
}

start_rechenkern() {
  mvn -B -q -f produktmodell/pom.xml install -DskipTests

  SERVER_PORT="$RECHENKERN_PORT" mvn -q -f rechenkern/pom.xml spring-boot:run \
    $(jvm_debug_option rechenkern)
}

KERNSYSTEM_TEMPLATE="$root/kernsystem/data"
KERNSYSTEM_RUNTIME="$root/kernsystem/runtime"

reset_store=false

prepare_store() {
  local action
  if [ "$reset_store" = true ]; then
    rm -rf "$KERNSYSTEM_RUNTIME"
    action="zurueckgesetzt"
  elif [ -d "$KERNSYSTEM_RUNTIME" ]; then
    local recorded current
    recorded="$(cat "$KERNSYSTEM_RUNTIME/.seed" 2>/dev/null || echo "unbekannt")"
    current="$(fingerprint_of "$KERNSYSTEM_TEMPLATE")"
    if [ "$recorded" != "$current" ]; then
      warn "kernsystem/data/ hat sich geaendert, seit kernsystem/runtime/ daraus angelegt wurde.
       Der Bestand kann Werte enthalten, die der Dienst nicht mehr liest.
       Zuruecksetzen mit: ./start.sh --bestand-zuruecksetzen"
    fi
    return 0
  else
    action="angelegt"
  fi

  [ -d "$KERNSYSTEM_TEMPLATE" ] \
    || error "Die Vorlage kernsystem/data/ fehlt -- ohne sie gibt es keinen Bestand zum Kopieren."

  mkdir -p "$KERNSYSTEM_RUNTIME"
  cp -R "$KERNSYSTEM_TEMPLATE/." "$KERNSYSTEM_RUNTIME/"
  fingerprint_of "$KERNSYSTEM_TEMPLATE" > "$KERNSYSTEM_RUNTIME/.seed"
  echo "  kernsystem/runtime aus der Vorlage kernsystem/data $action"
}

prepare_kernsystem() {
  check_java
  prepare_store
}

start_kernsystem() {

  SERVER_PORT="$KERNSYSTEM_PORT" \
  KERNSYSTEM_DATA_DIRECTORY="$KERNSYSTEM_RUNTIME" \
    mvn -q -f kernsystem/pom.xml spring-boot:run $(jvm_debug_option kernsystem)
}

prepare_wissen() {
  check_java

  if [ ! -f wissen/generated/stand.txt ]; then
    warn "wissen/generated/stand.txt fehlt -- ./wissen/build.sh erzeugt Dokumente, Indizes und Stand neu."
  elif [ "$(compute_status)" != "$(cat wissen/generated/stand.txt)" ]; then
    warn "Die Quellen unter wissen/ passen nicht mehr zu den erzeugten Dokumenten -- ./wissen/build.sh ausführen."
  fi

  export SPRING_AI_EMBEDDING_TRANSFORMER_CACHE_DIRECTORY="$root/wissen/modell"
}

start_wissen() {
  mvn -B -q -f produktmodell/pom.xml install -DskipTests

  SERVER_PORT="$WISSEN_PORT" mvn -q -f wissen/dienst/pom.xml spring-boot:run \
    $(jvm_debug_option wissen)
}

agents_built=false

build_agents() {
  [ "$agents_built" = true ] && return 0
  agents_built=true
  echo "agenten: mvn install im Reaktor (einmal je Lauf, ohne Tests)."
  mvn -B -q -f agenten/pom.xml install -DskipTests
}

wait_for_neighbour() {
  local caller="$1" component="$2" port="$3" seconds=0 limit=300
  is_selected "$component" || return 0
  reachable "http://localhost:${port}/actuator/health" && return 0
  echo "${caller}: warte auf ${component} (Port ${port})."
  while ! reachable "http://localhost:${port}/actuator/health"; do
    if [ "$seconds" -ge "$limit" ]; then
      warn "${caller}: ${component} war nach ${limit} s nicht bereit. Der Start läuft weiter -- die Ausgabe oben sagt, woran es liegt."
      return 0
    fi
    sleep 1
    seconds=$((seconds + 1))
  done
  return 0
}

prepare_agenten-beratung() {
  check_java
  check_gemini "der Beratungsagent"

  local missing=()
  is_selected kernsystem \
    || reachable "http://localhost:${KERNSYSTEM_PORT}/actuator/health" \
    || missing+=("Kernsystem (${KERNSYSTEM_PORT})")
  is_selected rechenkern \
    || reachable "http://localhost:${RECHENKERN_PORT}/actuator/health" \
    || missing+=("Rechenkern (${RECHENKERN_PORT})")
  is_selected wissen \
    || reachable "http://localhost:${WISSEN_PORT}/actuator/health" \
    || missing+=("Wissensdienst (${WISSEN_PORT})")

  if [ "${#missing[@]}" -gt 0 ]; then
    warn "agenten-beratung braucht Kernsystem, Rechenkern und Wissensdienst als MCP-Server; nicht erreichbar und nicht mitgestartet: ${missing[*]}. Der Dienst wird beim Verbindungsaufbau abbrechen. Vollständig:  ./start.sh rechenkern kernsystem wissen agenten-beratung agenten-orchestrator"
  fi

  build_agents
}

start_agenten-beratung() {
  wait_for_neighbour agenten-beratung kernsystem "$KERNSYSTEM_PORT"
  wait_for_neighbour agenten-beratung rechenkern "$RECHENKERN_PORT"
  wait_for_neighbour agenten-beratung wissen "$WISSEN_PORT"

  AGENTEN_BERATUNG_PORT="$AGENTEN_BERATUNG_PORT" \
  AGENTEN_CARD="${AGENTEN_CARD:-$root/agenten/cards/beratung.yaml}" \
  AGENTEN_BERATUNG_BASE_URL="${AGENTEN_BERATUNG_BASE_URL:-http://localhost:${AGENTEN_BERATUNG_PORT}}" \
  AGENTEN_MCP_KERNSYSTEM_URL="${AGENTEN_MCP_KERNSYSTEM_URL:-http://localhost:${KERNSYSTEM_PORT}}" \
  AGENTEN_MCP_RECHENKERN_URL="${AGENTEN_MCP_RECHENKERN_URL:-http://localhost:${RECHENKERN_PORT}}" \
  AGENTEN_MCP_WISSEN_URL="${AGENTEN_MCP_WISSEN_URL:-http://localhost:${WISSEN_PORT}}" \
    mvn -q -f agenten/beratung/pom.xml spring-boot:run $(jvm_debug_option agenten-beratung)
}

prepare_agenten-schadensfall() {
  check_java
  check_gemini "der Schadensfallagent"

  local missing=()
  is_selected kernsystem \
    || reachable "http://localhost:${KERNSYSTEM_PORT}/actuator/health" \
    || missing+=("Kernsystem (${KERNSYSTEM_PORT})")
  is_selected rechenkern \
    || reachable "http://localhost:${RECHENKERN_PORT}/actuator/health" \
    || missing+=("Rechenkern (${RECHENKERN_PORT})")
  is_selected wissen \
    || reachable "http://localhost:${WISSEN_PORT}/actuator/health" \
    || missing+=("Wissensdienst (${WISSEN_PORT})")

  if [ "${#missing[@]}" -gt 0 ]; then
    warn "agenten-schadensfall braucht Kernsystem, Rechenkern und Wissensdienst als MCP-Server; nicht erreichbar und nicht mitgestartet: ${missing[*]}. Der Dienst wird beim Verbindungsaufbau abbrechen. Vollständig:  ./start.sh rechenkern kernsystem wissen agenten-schadensfall"
  fi


  build_agents
}

start_agenten-schadensfall() {
  wait_for_neighbour agenten-schadensfall kernsystem "$KERNSYSTEM_PORT"
  wait_for_neighbour agenten-schadensfall rechenkern "$RECHENKERN_PORT"
  wait_for_neighbour agenten-schadensfall wissen "$WISSEN_PORT"

  AGENTEN_SCHADENSFALL_PORT="$AGENTEN_SCHADENSFALL_PORT" \
  AGENTEN_CARD="${AGENTEN_CARD:-$root/agenten/cards/schadensfall.yaml}" \
  AGENTEN_SCHADENSFALL_BASE_URL="${AGENTEN_SCHADENSFALL_BASE_URL:-http://localhost:${AGENTEN_SCHADENSFALL_PORT}}" \
  AGENTEN_MCP_KERNSYSTEM_URL="${AGENTEN_MCP_KERNSYSTEM_URL:-http://localhost:${KERNSYSTEM_PORT}}" \
  AGENTEN_MCP_RECHENKERN_URL="${AGENTEN_MCP_RECHENKERN_URL:-http://localhost:${RECHENKERN_PORT}}" \
  AGENTEN_MCP_WISSEN_URL="${AGENTEN_MCP_WISSEN_URL:-http://localhost:${WISSEN_PORT}}" \
  KERNSYSTEM_REST_URL="${KERNSYSTEM_REST_URL:-http://localhost:${KERNSYSTEM_PORT}}" \
    mvn -q -f agenten/schadensfall/pom.xml spring-boot:run \
      $(jvm_debug_option agenten-schadensfall)
}

prepare_agenten-orchestrator() {
  check_java
  check_gemini "der Orchestrator"

  is_selected kernsystem \
    || reachable "http://localhost:${KERNSYSTEM_PORT}/actuator/health" \
    || warn "agenten-orchestrator schlägt die Kundendaten über MCP im Kernsystem (${KERNSYSTEM_PORT}) nach; nicht erreichbar und nicht mitgestartet. Der Dienst wird beim Verbindungsaufbau abbrechen. Vollständig:  ./start.sh kernsystem agenten-orchestrator"

  is_selected agenten-beratung \
    || reachable "http://localhost:${AGENTEN_BERATUNG_PORT}/actuator/health" \
    || warn "agenten-orchestrator läuft ohne agenten-beratung (${AGENTEN_BERATUNG_PORT}), beantwortet dann aber jede Beratungsfrage mit einer Absage."

  is_selected agenten-schadensfall \
    || reachable "http://localhost:${AGENTEN_SCHADENSFALL_PORT}/actuator/health" \
    || warn "agenten-orchestrator läuft ohne agenten-schadensfall (${AGENTEN_SCHADENSFALL_PORT}); er entdeckt dann nur den Beratungsagenten."

  build_agents
}

start_agenten-orchestrator() {
  wait_for_neighbour agenten-orchestrator kernsystem "$KERNSYSTEM_PORT"
  wait_for_neighbour agenten-orchestrator agenten-beratung "$AGENTEN_BERATUNG_PORT"
  wait_for_neighbour agenten-orchestrator agenten-schadensfall "$AGENTEN_SCHADENSFALL_PORT"

  AGENTEN_ORCHESTRATOR_PORT="$AGENTEN_ORCHESTRATOR_PORT" \
  AGENTEN_CARD="${AGENTEN_CARD:-$root/agenten/cards/orchestrator.yaml}" \
  AGENTEN_ORCHESTRATOR_BASE_URL="${AGENTEN_ORCHESTRATOR_BASE_URL:-http://localhost:${AGENTEN_ORCHESTRATOR_PORT}}" \
  AGENTEN_MCP_KERNSYSTEM_URL="${AGENTEN_MCP_KERNSYSTEM_URL:-http://localhost:${KERNSYSTEM_PORT}}" \
  AGENTEN_BERATUNG_URL="${AGENTEN_BERATUNG_URL:-http://localhost:${AGENTEN_BERATUNG_PORT}}" \
  AGENTEN_SCHADENSFALL_URL="${AGENTEN_SCHADENSFALL_URL:-http://localhost:${AGENTEN_SCHADENSFALL_PORT}}" \
    mvn -q -f agenten/orchestrator/pom.xml spring-boot:run \
      $(jvm_debug_option agenten-orchestrator)
}

prepare_sachbearbeiter-ui() {
  check_node
  check_dependencies sachbearbeiter-ui
  check_vite_entry sachbearbeiter-ui
}

start_sachbearbeiter-ui() {
  SCHADENSFALLAGENT_URL="${SCHADENSFALLAGENT_URL:-http://localhost:${AGENTEN_SCHADENSFALL_PORT}}" \
  run_vite sachbearbeiter-ui "$SACHBEARBEITER_UI_PORT"
}

prepare_kunden-chat() {
  check_node
  check_dependencies kunden-chat
  check_vite_entry kunden-chat

  [ "${AGENT_CLIENT:-a2a}" = "a2a" ] || return 0
  is_selected agenten-orchestrator \
    || reachable "http://localhost:${AGENTEN_ORCHESTRATOR_PORT}/actuator/health" \
    || warn "kunden-chat spricht den Orchestrator (${AGENTEN_ORCHESTRATOR_PORT}) an; der läuft nicht und ist nicht mitgestartet. Jede Nachricht bekommt dann eine Absage. Vollständig:  ./start.sh rechenkern kernsystem wissen agenten-beratung agenten-orchestrator kunden-chat   -- oder ohne Java:  AGENT_CLIENT=mock ./start.sh kunden-chat"
}

start_kunden-chat() {
  AGENT_URL="${AGENT_URL:-http://localhost:${AGENTEN_ORCHESTRATOR_PORT}/}" \
  WISSEN_URL="${WISSEN_URL:-http://localhost:${WISSEN_PORT}/api/v1}" \
  BODY_SIZE_LIMIT="${BODY_SIZE_LIMIT:-10M}" \
    run_vite kunden-chat "$KUNDEN_CHAT_PORT"
}


pids=()
running=()
cleaned_up=false

kill_tree() {
  local pid="${1:-}" children child
  [ -n "$pid" ] || return 0
  children="$(pgrep -P "$pid" 2>/dev/null || true)"
  kill -TERM "$pid" 2>/dev/null || true
  for child in $children; do
    kill_tree "$child"
  done
}

cleanup() {
  [ "$cleaned_up" = true ] && return 0
  cleaned_up=true
  [ "${#pids[@]}" -eq 0 ] && return 0

  echo
  echo "Beende das Lab."
  local i was_running
  for i in "${!pids[@]}"; do
    was_running=false
    kill -0 "${pids[$i]}" 2>/dev/null && was_running=true
    kill_tree "${pids[$i]}"
    free_port "$(port_of "${running[$i]}")"
    [ "$was_running" = true ] && echo "  ${running[$i]} gestoppt"
  done
  return 0
}

trap cleanup INT TERM EXIT


help_text() {
  cat <<'END'
Startet das Lab lokal.

  ./start.sh                          alle Bausteine
  ./start.sh wissen agenten-beratung  nur die genannten
  ./start.sh --tracelog               vollständiges Tracelog schreiben
  ./start.sh --debug                  Debug-Ports öffnen, IDE hängt sich an
  ./start.sh --bestand-zuruecksetzen  Kundendaten und Fälle aus der Vorlage neu
  ./start.sh --hilfe                  diese Übersicht

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

  WISSEN_PORT=9000 ./start.sh wissen

8080 gehoert dem Kernsystem, so wie es in oas/openapi.yaml steht. Das Formular
des Rechnungsgenerators will denselben Port und wird von diesem Skript nicht
gestartet -- wer beides braucht:

  KERNSYSTEM_PORT=8083 ./start.sh

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
  agenten-orchestrator        den Beratungsagenten fuer die Fachauskunft und
                              den Schadensfallagenten fuer die Fallpruefung;
                              werden sie mitgestartet, wartet der Orchestrator
                              beim Start auf sie, damit die Discovery gleich
                              gelingt -- fehlt einer dauerhaft, laedt der
                              Orchestrator die Karte spaeter selbst nach
  kunden-chat                 den Orchestrator -- zum Antworten. Ohne Java:
                              AGENT_CLIENT=mock ./start.sh kunden-chat

Der Schadensfallagent faengt von sich aus an:
  Er sieht alle 10 s nach Faellen im Status "eingereicht", uebernimmt jeden
  davon und prueft ihn. Fuer eine Vorfuehrung, in der nichts von selbst
  passieren soll, laesst sich der Takt abschalten -- er prueft dann nur noch
  auf Anforderung ueber den A2A-Skill fall_pruefen:

    SCHADENSFALL_POLL_ENABLED=false ./start.sh

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

    ./start.sh --debug                  alle Bausteine mit Debug-Port
    ./start.sh --debug kernsystem       nur diesen einen

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

    SCHADENSFALL_POLL_ENABLED=false ./start.sh --debug agenten-schadensfall

Tracelog (--tracelog):
  Schreibt die vollständige Agenten- und MCP-Kommunikation nach tracelog/ --
  je Dienst eine Datei, eine Zeile JSON je Ereignis. Gedacht zum Nachsehen,
  wenn ein Agent etwas anderes getan hat als erwartet: Jeder A2A-Sprung, jeder
  Werkzeugaufruf samt Rohergebnis und jede Entscheidung im Modell stehen darin,
  mit Zeit und Gesprächskennung.

  Standardmäßig aus, und der Schalter gilt nur für diesen Start. Alles in
  zeitlicher Reihenfolge lesen:

    cat tracelog/*.jsonl | jq -s 'sort_by(.timestamp)[]'

  Ein einzelnes Gespräch:

    cat tracelog/*.jsonl | jq -s 'map(select(.gespraech=="<id>")) | sort_by(.timestamp)[]'

  Das Verzeichnis lässt sich umlenken:  AGENTEN_TRACELOG=/tmp/lauf7 ./start.sh

  Das Tracelog enthält Klartext aus dem Gespräch samt Kundennummer und
  Rohergebnissen der Werkzeuge. tracelog/ ist deshalb nicht versioniert.

Bestand des Kernsystems (--bestand-zuruecksetzen):
  Kunden und Schadensfälle liegen zweimal. kernsystem/data/ ist die Vorlage:
  versioniert und zur Laufzeit unberührt. kernsystem/runtime/ ist der Stand
  dieses Rechners, nicht versioniert, und nur dorthin schreibt der Dienst.
  Eine Vorführung lässt den Arbeitsbaum damit sauber -- das frühere
  "git checkout kernsystem/data" von Hand entfällt.

  Kopiert wird beim Start nur, wenn kernsystem/runtime/ noch fehlt. Gibt es
  das Verzeichnis, bleibt es unangetastet: Angelegte Kunden und geprüfte Fälle
  überdauern einen Neustart, so wie bisher. Zurück auf die Seed-Daten geht es
  nur auf Ansage:

    ./start.sh --bestand-zuruecksetzen

  Der Schalter greift beim Vorbereiten des Kernsystems. Wer ihn setzt, ohne
  das Kernsystem mitzustarten, bekommt einen Hinweis und sonst nichts.

Die Agentenmodule sind ein Maven-Reaktor; das Skript legt sie einmal je Lauf
mit "mvn install -DskipTests" ins lokale Repository, bevor ein Dienst startet.

Die erzeugten Dokumente und Suchindizes liegen fertig im Repository. Wer
Quellen unter wissen/ ändert, baut mit ./wissen/build.sh neu.

Geprüft wird nur, was gestartet wird. Beenden mit Strg+C; das Skript stoppt
alles, was es selbst gestartet hat. Wenn das einmal nicht reicht -- ein Fenster
weg, ein Dienst haengengeblieben --, raeumt ./stop.sh hinterher.
END
}

selected=()
is_selected() {
  local candidate="$1" name
  [ "${#selected[@]}" -eq 0 ] && return 1
  for name in "${selected[@]}"; do
    [ "$name" = "$candidate" ] && return 0
  done
  return 1
}

while [ $# -gt 0 ]; do
  case "$1" in
    -h|--hilfe|--help)
      help_text
      exit 0
      ;;
    --tracelog)
      export AGENTEN_TRACELOG="${AGENTEN_TRACELOG:-$root/tracelog}"
      ;;
    --bestand-zuruecksetzen)
      reset_store=true
      ;;
    --debug)
      debug=true
      ;;
    *)
      is_component "$1" \
        || error "Unbekannter Baustein '$1'. Möglich: ${COMPONENTS[*]}"
      is_selected "$1" || selected+=("$1")
      ;;
  esac
  shift
done

[ "${#selected[@]}" -eq 0 ] && selected=("${COMPONENTS[@]}")

if [ "$reset_store" = true ] && ! is_selected kernsystem; then
  warn "--bestand-zuruecksetzen wirkt nur, wenn das Kernsystem mitstartet -- der Bestand bleibt unveraendert."
fi

for name in "${selected[@]}"; do
  check_port_free "$(port_of "$name")" "$name"
  [ "$debug" = true ] \
    && check_port_free "$(debug_port_of "$name")" "${name}, Debug-Port"
done

echo "Lab: ${selected[*]}"
if [ "$debug" = true ]; then
  echo "Debug: Ports offen, die IDE haengt sich an (Adressen stehen unten)"
fi
if [ -n "${AGENTEN_TRACELOG:-}" ]; then
  echo "Tracelog: ${AGENTEN_TRACELOG} (je Dienst eine .jsonl)"
fi
echo

for name in "${selected[@]}"; do
  "prepare_${name}"
done
echo

single=false
[ "${#selected[@]}" -eq 1 ] && single=true

index=0
for name in "${selected[@]}"; do
  echo "Starte ${name} ($(description_of "$name")) auf Port $(port_of "$name")."
  if [ "$single" = true ]; then
    "start_${name}" &
  else
    ( "start_${name}" 2>&1 | with_prefix "$name" "${colors[$((index % ${#colors[@]}))]}" ) &
  fi
  pids+=("$!")
  running+=("$name")
  index=$((index + 1))
done

echo
echo "Warte, bis alles bereit ist."
if is_selected wissen; then
  echo "Beim ersten Start lädt der Wissensdienst sein Einbettungsmodell"
  echo "(rund 490 MB) -- das dauert einmalig länger."
fi
if is_selected agenten-orchestrator; then
  echo "Der Orchestrator entdeckt seine Subagenten beim Start per Agent Card;"
  echo "die Zeile 'Agent entdeckt: ...' in seiner Ausgabe bestätigt das."
fi

ready=()
failed=()
for i in "${!running[@]}"; do
  if wait_for "${running[$i]}" "${pids[$i]}"; then
    ready+=("${running[$i]}")
  else
    failed+=("${running[$i]}")
  fi
done

echo
if [ "${#ready[@]}" -gt 0 ]; then
  echo "Das Lab läuft:"
  for name in "${ready[@]}"; do
    echo "  ${name}"
    addresses_of "$name" | while IFS=$'\t' read -r label address; do
      padding=$((12 - ${#label}))
      [ "$padding" -lt 1 ] && padding=1
      printf '    %s%*s%s\n' "$label" "$padding" "" "$address"
    done
  done
else
  echo "Kein Baustein ist bereit geworden."
fi

if [ "${#failed[@]}" -gt 0 ]; then
  echo
  echo "Nicht bereit: ${failed[*]}"
fi

echo
echo "Beenden mit Strg+C."
echo

wait || true
