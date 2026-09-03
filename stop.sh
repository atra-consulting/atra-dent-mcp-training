#!/usr/bin/env bash

if [ -z "${BASH_VERSION:-}" ] || shopt -qo posix 2>/dev/null; then
  exec bash "$0" "$@"
fi

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$root"

# shellcheck source=components.sh
. ./components.sh

error() {
  echo "FEHLER: $*" >&2
  exit 1
}

help_text() {
  cat <<'END'
Beendet das Lab. Fuer den Fall, dass Strg+C nicht gereicht hat.

  ./stop.sh                        alles
  ./stop.sh wissen kernsystem      nur die genannten
  ./stop.sh --hilfe                diese Uebersicht

Gearbeitet wird in drei Schritten, und jeder darf leer ausgehen:

  1. Ein noch laufendes ./start.sh bekommt ein TERM. Es hat einen eigenen
     Trap und raeumt seine Kinder selbst weg -- der saubere Weg, und meistens
     ist danach schon nichts mehr da.
  2. Was dann noch auf einem Port des Labs lauscht und zu diesem Repository
     gehoert, bekommt TERM und, wenn es das ueberlebt, KILL. Das sind die acht
     Ports der Bausteine und die acht Debug-Ports, die ./start.sh --debug
     oeffnet. Wer den Port nur benutzt -- eine IntelliJ am Debug-Port, ein
     Browser auf der Oberflaeche -- ist kein Ziel.
  3. Uebrig gebliebene Prozesse des Labs -- ein mvn ohne Port, eine Vite, die
     sich nicht mehr meldet -- werden anhand ihres Arbeitsverzeichnisses
     gefunden und beendet.

Schritt 1 und 3 laufen nur, wenn kein Baustein genannt ist: Wer gezielt
"./stop.sh kunden-chat" sagt, will das uebrige Lab behalten.

Gefunden wird ausschliesslich, was zu diesem Repository gehoert -- ueber die
Ports aus .env und dieser Datei und ueber Prozesse, deren Arbeitsverzeichnis
unter

  ROOT

liegt. Ein Maven oder ein Node aus einem anderen Projekt bleibt unbehelligt.

Der Arztservice wird nicht gestoppt. Er laeuft fremd gehostet und war nie ein
Prozess auf diesem Rechner.
END
}

selected=()
while [ $# -gt 0 ]; do
  case "$1" in
    -h|--hilfe|--help)
      help_text | sed "s|^  ROOT$|  ${root}|"
      exit 0
      ;;
    -*)
      error "Unbekannte Option '$1'. './stop.sh --hilfe' zeigt die Moeglichkeiten."
      ;;
    *)
      is_component "$1" \
        || error "Unbekannter Baustein '$1'. Moeglich: ${COMPONENTS[*]}"
      case " ${selected[*]:-} " in
        *" $1 "*) ;;
        *) selected+=("$1") ;;
      esac
      ;;
  esac
  shift
done

everything=false
if [ "${#selected[@]}" -eq 0 ]; then
  everything=true
  selected=("${COMPONENTS[@]}")
fi

command -v lsof >/dev/null 2>&1 \
  || error "'lsof' wurde nicht gefunden, und ohne das Werkzeug laesst sich nicht feststellen, wer auf den Ports des Labs sitzt."

stopped=0

own_pid=$$
is_own() {
  local pid="$1"
  [ "$pid" = "$own_pid" ] || [ "$pid" = "$PPID" ]
}

signal_pid() {
  local pid="$1" signal="$2"
  is_own "$pid" && return 0
  kill "-${signal}" "$pid" 2>/dev/null || true
}

alive() {
  kill -0 "$1" 2>/dev/null
}

wait_gone() {
  local pid="$1" limit="${2:-5}" seconds=0
  while [ "$seconds" -lt "$limit" ]; do
    alive "$pid" || return 0
    sleep 1
    seconds=$((seconds + 1))
  done
  alive "$pid" && return 1
  return 0
}

pids_on_port() {
  lsof -ti ":$1" -sTCP:LISTEN 2>/dev/null || true
}

working_directory_of() {
  lsof -a -d cwd -Fn -p "$1" 2>/dev/null | sed -n 's/^n//p' | head -1
}

belongs_to_lab() {
  local directory
  directory="$(working_directory_of "$1")"
  case "$directory" in
    "$root"|"$root"/*) return 0 ;;
    *) return 1 ;;
  esac
}



stop_start_script() {
  local pids pid found=false
  pids="$(pgrep -f "bash ${root}/start.sh|bash \./start\.sh" 2>/dev/null || true)"
  [ -n "$pids" ] || return 0
  for pid in $pids; do
    is_own "$pid" && continue
    belongs_to_lab "$pid" || continue
    if [ "$found" = false ]; then
      echo "start.sh laeuft noch -- TERM, damit es selbst aufraeumt."
      found=true
    fi
    signal_pid "$pid" TERM
    stopped=$((stopped + 1))
  done
  [ "$found" = true ] || return 0
  for pid in $pids; do
    wait_gone "$pid" 8 || signal_pid "$pid" KILL
  done
  return 0
}



free_port() {
  local port="$1" label="$2" pids pid ours="" remaining
  pids="$(pids_on_port "$port")"
  [ -n "$pids" ] || return 0

  for pid in $pids; do
    is_own "$pid" && continue
    if belongs_to_lab "$pid"; then
      ours="${ours} ${pid}"
    else
      echo "  ${label} (${port}): der Port gehoert einem fremden Prozess ($(ps -o comm= -p "$pid" 2>/dev/null | head -1 | sed 's|.*/||'), ${pid}) -- bleibt unbehelligt."
    fi
  done
  [ -n "$ours" ] || return 0

  for pid in $ours; do
    signal_pid "$pid" TERM
  done
  for pid in $ours; do
    wait_gone "$pid" 5 || true
  done

  remaining=""
  for pid in $ours; do
    alive "$pid" && remaining="${remaining} ${pid}"
  done
  for pid in $remaining; do
    signal_pid "$pid" KILL
  done
  [ -n "$remaining" ] && sleep 1

  for pid in $ours; do
    if alive "$pid"; then
      echo "  ${label} (${port}): laesst sich nicht beenden (${pid})."
      return 0
    fi
  done
  echo "  ${label} (${port}): gestoppt"
  stopped=$((stopped + 1))
  return 0
}



sweep_strays() {
  local pids pid command found=false
  pids="$(pgrep -f 'spring-boot:run|vite|start\.sh' 2>/dev/null || true)"
  [ -n "$pids" ] || return 0
  for pid in $pids; do
    is_own "$pid" && continue
    alive "$pid" || continue
    belongs_to_lab "$pid" || continue
    command="$(ps -o command= -p "$pid" 2>/dev/null | cut -c1-70)"
    if [ "$found" = false ]; then
      echo "Rest ohne Port:"
      found=true
    fi
    echo "  ${pid}  ${command}"
    signal_pid "$pid" TERM
    wait_gone "$pid" 5 || signal_pid "$pid" KILL
    stopped=$((stopped + 1))
  done
  return 0
}


if [ "$everything" = true ]; then
  echo "Beende das Lab."
  stop_start_script
else
  echo "Beende: ${selected[*]}"
fi

for name in "${selected[@]}"; do
  free_port "$(port_of "$name")" "$name"
  free_port "$(debug_port_of "$name")" "${name}, Debug-Port"
done

[ "$everything" = true ] && sweep_strays

echo
if [ "$stopped" -eq 0 ]; then
  echo "Nichts zu beenden -- das Lab lief nicht."
else
  echo "Fertig."
fi
