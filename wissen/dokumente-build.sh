#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

command -v typst >/dev/null 2>&1 || {
  echo "FEHLER: 'typst' wurde nicht gefunden. Typst erzeugt die Bedingungswerke: https://typst.app (macOS: brew install typst)." >&2
  exit 1
}

stand="$(awk -F': *' '/^stand:/ { print $2; exit }' wissen/daten/tarife.yaml)"
if [ -n "$stand" ]; then
  SOURCE_DATE_EPOCH="$(TZ=UTC date -j -f '%Y-%m-%d %H:%M:%S' "$stand 00:00:00" +%s 2>/dev/null \
    || TZ=UTC date -d "$stand 00:00:00 UTC" +%s 2>/dev/null || true)"
  [ -n "$SOURCE_DATE_EPOCH" ] && export SOURCE_DATE_EPOCH
fi

documents=(
  atra-dent-smart-avb
  atra-dent-balance-avb
  atra-dent-brillant-avb
  atra-dent-tarifvergleich
  atra-dent-goz-zuordnung
  atra-dent-beratungshandbuch
)

pdf_directory="wissen/generated/pdf"
html_directory="wissen/generated/html"
mkdir -p "$pdf_directory" "$html_directory"

echo "PDF-Fassung (für den Menschen)"
for name in "${documents[@]}"; do
  source="wissen/dokumente/$name.typ"
  target="$pdf_directory/$name.pdf"
  printf '  %-32s -> %s\n' "$name.typ" "$target"
  typst compile --root . "$source" "$target"
done

echo
echo "HTML-Fassung (für den Wissensdienst)"
for name in "${documents[@]}"; do
  source="wissen/dokumente/$name.typ"
  target="$html_directory/$name.html"
  printf '  %-32s -> %s\n' "$name.typ" "$target"
  typst compile --root . --features html --format html "$source" "$target"
done

echo
printf 'Fertig. %s PDF in %s/, %s HTML in %s/\n' \
  "$(find "$pdf_directory" -name '*.pdf' | wc -l | tr -d ' ')" "$pdf_directory" \
  "$(find "$html_directory" -name '*.html' | wc -l | tr -d ' ')" "$html_directory"
