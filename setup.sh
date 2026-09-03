#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$root"

help_text() {
  cat <<'END'
Sets the lab up: checks the tools and installs the dependencies of every
subproject.

  ./setup.sh           check and install
  ./setup.sh --hilfe   this overview

Checked:
  java 25, mvn         Rechenkern, Kernsystem, Wissensdienst, agenten
  node 20.19+, npm     sachbearbeiter-ui, kunden-chat
  typst, python3       hint only -- rebuilding the documents, the Rechnung form
  curl, lsof           hint only -- the health and port checks of start.sh
  .env                 hint only -- GEMINI_API_KEY for the three agents

Installed:
  mvn install -DskipTests      produktmodell (shared module, without tests)
  mvn dependency:go-offline    rechenkern, kernsystem, wissen/dienst
  mvn install -DskipTests      agenten (reactor, as in start.sh)
  npm install                  sachbearbeiter-ui, kunden-chat

Missing tools are collected and reported together at the end; whatever can be
installed with what is there is installed. If something is missing the script
ends with exit code 1 -- just run it again after installing.
END
}

case "${1:-}" in
  -h|--hilfe|--help)
    help_text
    exit 0
    ;;
  '') ;;
  *)
    echo "FEHLER: Unbekannte Option '$1'. './setup.sh --hilfe' zeigt die Möglichkeiten." >&2
    exit 1
    ;;
esac

warn() {
  echo "Hinweis: $*" >&2
}


missing=()
hints=()

java_ready=false
node_ready=false

check_java() {
  local ok=true major_version
  if command -v java >/dev/null 2>&1; then
    major_version="$(java -version 2>&1 | awk -F'"' '/version/ { split($2, part, "."); print part[1]; exit }')"
    if [ "${major_version:-0}" -lt 25 ]; then
      ok=false
      missing+=("java -- Version ${major_version:-unbekannt} ist zu alt, die Dienste verlangen Java 25 (LTS): https://adoptium.net")
    fi
  else
    ok=false
    missing+=("java -- Die Dienste brauchen Java 25 (LTS), z. B. Eclipse Temurin: https://adoptium.net")
  fi
  if ! command -v mvn >/dev/null 2>&1; then
    ok=false
    missing+=("mvn -- Die Dienste werden mit Maven gebaut und gestartet: https://maven.apache.org (macOS: brew install maven)")
  fi
  [ "$ok" = true ] && java_ready=true
  return 0
}

check_node() {
  local ok=true version major minor
  if command -v node >/dev/null 2>&1; then
    version="$(node --version | sed 's/^v//')"
    major="${version%%.*}"
    minor="$(echo "$version" | cut -d. -f2)"
    if [ "$major" -lt 20 ] || { [ "$major" -eq 20 ] && [ "$minor" -lt 19 ]; }; then
      ok=false
      missing+=("node -- Version ${version} ist zu alt, Vite 8 verlangt 20.19+ oder 22.12+: https://nodejs.org")
    fi
  else
    ok=false
    missing+=("node -- Die Oberflächen brauchen Node.js 20.19+ (oder 22.12+): https://nodejs.org (macOS: brew install node)")
  fi
  if ! command -v npm >/dev/null 2>&1; then
    ok=false
    missing+=("npm -- gehört zu Node.js; eine unvollständige Installation bitte erneuern")
  fi
  [ "$ok" = true ] && node_ready=true
  return 0
}

optional_tool() {
  local name="$1" purpose="$2"
  command -v "$name" >/dev/null 2>&1 || hints+=("${name} fehlt -- ${purpose}")
}

echo "Pruefe Werkzeuge."
check_java
check_node

optional_tool typst "only for ./wissen/build.sh, ./generator/rechnungen/build.sh, ./generator/nachweise/build.sh and ./generator/reconcile.sh; the finished documents are checked in under wissen/generated/ and submissions/: https://typst.app"
optional_tool python3 "only for the Rechnung form of the generator (generator/rechnungen/frontend.py) and generator/reconcile.sh, whose --goae-only mode needs only python3"
optional_tool curl "start.sh cannot report when a building block is ready without curl"
optional_tool lsof "start.sh cannot detect occupied ports without lsof"

[ -f .env ] \
  || hints+=(".env fehlt -- Orchestrator, Beratungsagent und Schadensfallagent brauchen GEMINI_API_KEY, und ein fremder Agent, dessen Karte einen Schluessel verlangt, braucht ihn in seinem Katalogeintrag -- fuer den Arztservice AGENTEN_ARZTSERVICE_API_KEY: cp .env.beispiel .env und Schluessel eintragen")

if [ ! -d wissen/modell ] || [ -z "$(ls -A wissen/modell 2>/dev/null)" ]; then
  hints+=("Der Wissensdienst laedt beim ersten Start sein Einbettungsmodell (rund 490 MB) nach wissen/modell/ -- das dauert einmalig laenger")
fi


echo

if [ "$java_ready" = true ]; then
  echo "Maven lädt die Abhängigkeiten der Java-Dienste (beim ersten Mal einige Minuten, Ausgabe nur bei Fehlern)."
  mvn -B -q -f produktmodell/pom.xml install -DskipTests
  for pom in rechenkern/pom.xml kernsystem/pom.xml wissen/dienst/pom.xml; do
    echo "  ${pom%/pom.xml}"
    mvn -B -q -f "$pom" dependency:go-offline
  done

  echo "  agenten (mvn install im Reaktor, ohne Tests)"
  mvn -B -q -f agenten/pom.xml install -DskipTests
else
  warn "Java-Dienste übersprungen (rechenkern, kernsystem, wissen, agenten) -- Java oder Maven fehlt, siehe unten."
fi

echo

if [ "$node_ready" = true ]; then
  for directory in sachbearbeiter-ui kunden-chat; do
    echo "npm install in ${directory}."
    npm --prefix "$directory" install
  done
else
  warn "Oberflächen übersprungen (sachbearbeiter-ui, kunden-chat) -- Node.js oder npm fehlt, siehe unten."
fi


echo

if [ "${#hints[@]}" -gt 0 ]; then
  echo "Hinweise:"
  for hint in "${hints[@]}"; do
    echo "  - ${hint}"
  done
  echo
fi

if [ "${#missing[@]}" -gt 0 ]; then
  echo "Es fehlt:"
  for entry in "${missing[@]}"; do
    echo "  - ${entry}"
  done
  echo
  echo "Nach der Installation ./setup.sh erneut ausführen."
  exit 1
fi

echo "Alles eingerichtet. ./start.sh startet das Lab."
