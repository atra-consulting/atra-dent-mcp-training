#!/usr/bin/env bash

set -uo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$root"

probe="generator/rechnungen/tests/sonde.typ"
failures=0

run() {
  typst compile --root . --input fall="$1" --format png "$probe" /dev/null 2>&1
}

echo "Gute Fälle (müssen durchlaufen)"
for f in generator/rechnungen/tests/cases-valid/*.yaml; do
  path="/${f}"
  if output="$(run "$path")"; then
    printf '  OK      %s\n' "$(basename "$f")"
  else
    printf '  FEHLER  %s — bricht ab, sollte aber durchlaufen\n' "$(basename "$f")"
    printf '%s\n' "$output" | sed 's/^/          /'
    failures=$((failures + 1))
  fi
done

echo
echo "Fehlerhafte Fälle (müssen abbrechen)"
for f in generator/rechnungen/tests/cases-invalid/*.yaml; do
  path="/${f}"
  if run "$path" >/dev/null; then
    printf '  FEHLER  %s — läuft durch, sollte aber abbrechen\n' "$(basename "$f")"
    failures=$((failures + 1))
  else
    printf '  OK      %s\n' "$(basename "$f")"
  fi
done

echo
if [ "$failures" -eq 0 ]; then
  echo "Alle Prüfungen bestanden."
else
  echo "$failures Prüfung(en) fehlgeschlagen."
  exit 1
fi
