#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

error() {
  echo "FEHLER: $*" >&2
  exit 1
}

command -v java >/dev/null 2>&1 \
  || error "'java' wurde nicht gefunden. Der Indexbau braucht Java 25 (LTS), z. B. Eclipse Temurin: https://adoptium.net."
command -v mvn >/dev/null 2>&1 \
  || error "'mvn' wurde nicht gefunden. Der Wissensdienst wird mit Maven gebaut: https://maven.apache.org (macOS: brew install maven)."

major_version="$(java -version 2>&1 | awk -F'"' '/version/ { split($2, part, "."); print part[1]; exit }')"
if [ "${major_version:-0}" -lt 25 ]; then
  error "Java ${major_version:-unbekannter Version} ist zu alt. Der Wissensdienst verlangt Java 25."
fi

html_count="$(find wissen/generated/html -name '*.html' 2>/dev/null | wc -l | tr -d ' ')"
if [ "$html_count" -eq 0 ]; then
  error "Keine HTML-Fassungen unter wissen/generated/html/. Zuerst ./wissen/dokumente-build.sh ausführen."
fi

export SPRING_AI_EMBEDDING_TRANSFORMER_CACHE_DIRECTORY="$root/wissen/modell"

if [ ! -d wissen/modell ] || [ -z "$(ls -A wissen/modell 2>/dev/null)" ]; then
  echo "Erster Lauf: das Einbettungsmodell (rund 490 MB) wird nach wissen/modell/ geladen -- das dauert einmalig."
fi

mvn -B -q -f produktmodell/pom.xml install -DskipTests

echo "Baue die Vektorindizes (Spring-Profil indexbau)."
mvn -q -f wissen/dienst/pom.xml spring-boot:run -Dspring-boot.run.profiles=indexbau

echo
echo "Fertig. Indizes unter wissen/generated/index/:"
ls -lh wissen/generated/index/*.json
