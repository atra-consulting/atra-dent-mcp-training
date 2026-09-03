#!/usr/bin/env bash

set -euo pipefail

wurzel="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$wurzel"

ziel_verzeichnis="submissions/rechnungen/pdf"
mkdir -p "$ziel_verzeichnis"

if [ "${1:-}" = "--pruefen" ]; then
  lastfall="generator/rechnungen/tests/wrap-case.yaml"
  ziel_verzeichnis="generator/rechnungen/pruefung"
  mkdir -p "$ziel_verzeichnis"

  echo "Prüfmodus: Umbruchtest über alle Stile"
  for stil in klassisch modern verrechnungsstelle landpraxis klinik; do
    ziel="$ziel_verzeichnis/umbruch-$stil.pdf"
    printf '  %-20s -> %s\n' "$stil" "$ziel"
    typst compile --root . --pdf-standard a-2b \
      --input fall="/$lastfall" \
      --input stil="$stil" \
      generator/rechnungen/rechnung.typ "$ziel"
  done
  echo
  echo "Fertig. Fünf Fassungen in $ziel_verzeichnis/ — Umbrüche von Hand ansehen."
  exit 0
fi

echo "Rechnungen"
anzahl=0
for quelle in submissions/rechnungen/cases/*.yaml; do
  [ -e "$quelle" ] || continue
  name="$(basename "${quelle%.yaml}")"
  ziel="$ziel_verzeichnis/$name.pdf"
  printf '  %-34s -> %s\n' "$name.yaml" "$ziel"
  typst compile --root . --pdf-standard a-2b --input fall="/$quelle" generator/rechnungen/rechnung.typ "$ziel"
  anzahl=$((anzahl + 1))
done

echo
echo "Fertig. $anzahl Rechnung(en) in $ziel_verzeichnis/"
