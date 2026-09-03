#!/usr/bin/env bash

set -euo pipefail

wurzel="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$wurzel"

dokumente=(befund historie fragebogen)

if [ "${1:-}" = "--pruefen" ]; then
  lastfall="${2:-submissions/nachweise/cases/10002-schuster.yaml}"
  ziel_verzeichnis="generator/nachweise/pruefung"
  mkdir -p "$ziel_verzeichnis"

  echo "Prüfmodus: $(basename "$lastfall") über alle Stile"
  for stil in klassisch modern mvz landpraxis klinik; do
    ziel="$ziel_verzeichnis/befund-$stil.pdf"
    printf '  %-14s -> %s\n' "$stil" "$ziel"
    typst compile --root . --pdf-standard a-2b \
      --input fall="/$lastfall" \
      --input stil="$stil" \
      generator/nachweise/befund.typ "$ziel"
  done
  echo
  echo "Fertig. Fünf Fassungen in $ziel_verzeichnis/ — Umbrüche von Hand ansehen."
  exit 0
fi

echo "Nachweise"
anzahl=0
for quelle in submissions/nachweise/cases/*.yaml; do
  [ -e "$quelle" ] || continue
  name="$(basename "${quelle%.yaml}")"
  ziel_verzeichnis="submissions/nachweise/pdf/$name"
  mkdir -p "$ziel_verzeichnis"
  printf '  %-24s ->' "$name"
  for dokument in "${dokumente[@]}"; do
    typst compile --root . --pdf-standard a-2b \
      --input fall="/$quelle" \
      "generator/nachweise/$dokument.typ" "$ziel_verzeichnis/$dokument.pdf"
    printf ' %s' "$dokument"
  done
  printf '\n'
  anzahl=$((anzahl + 1))
done

echo
echo "Fertig. $anzahl Einreichung(en), $((anzahl * ${#dokumente[@]})) Dokumente in submissions/nachweise/pdf/"
