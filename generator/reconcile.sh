#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

goae_only=0
for arg in "$@"; do
  case "$arg" in
    --goae-only)
      goae_only=1
      ;;
    *)
      echo "FEHLER: unbekannte Option '$arg'." >&2
      exit 1
      ;;
  esac
done

command -v python3 >/dev/null 2>&1 || {
  echo "FEHLER: 'python3' wurde nicht gefunden." >&2
  exit 1
}

failures=0

if [ "$goae_only" -eq 0 ]; then
  command -v typst >/dev/null 2>&1 || {
    echo "FEHLER: 'typst' wurde nicht gefunden. Typst erzeugt die Rechnungen: https://typst.app (macOS: brew install typst)." >&2
    exit 1
  }

  normalize_py="$root/generator/normalize.py"

  temp_dir="$(mktemp -d)"
  trap 'rm -rf "$temp_dir"' EXIT

  echo "Rechnungen: Neubau gegen submissions/rechnungen/pdf/"
  for case_file in submissions/rechnungen/cases/*.yaml; do
    [ -e "$case_file" ] || continue
    name="$(basename "${case_file%.yaml}")"
    fresh="$temp_dir/$name.pdf"
    committed="submissions/rechnungen/pdf/$name.pdf"

    if ! typst compile --root . --pdf-standard a-2b --input fall="/$case_file" \
      generator/rechnungen/rechnung.typ "$fresh"; then
      printf '  FEHLER     %s liess sich nicht neu bauen\n' "$name.pdf" >&2
      failures=1
      continue
    fi

    if [ ! -f "$committed" ]; then
      printf '  FEHLT      %s ist unter submissions/rechnungen/pdf/ nicht abgelegt\n' "$name.pdf" >&2
      failures=1
      continue
    fi

    if python3 "$normalize_py" "$fresh" "$committed"; then
      printf '  OK         %s\n' "$name.pdf"
    else
      printf '  ABWEICHUNG %s weicht vom abgelegten Stand ab\n' "$name.pdf" >&2
      failures=1
    fi
  done

  echo
fi

echo "GOAe-Nummern: wissen/daten/goae-auszug.yaml gegen submissions/rechnungen/cases/"
if ! python3 "$root/generator/goae_reconcile.py" "$root"; then
  failures=1
fi

echo
if [ "$failures" -eq 0 ]; then
  if [ "$goae_only" -eq 1 ]; then
    echo "Fertig. GOAe-Auszug stimmt mit submissions/rechnungen/cases/ ueberein."
  else
    echo "Fertig. Rechnungen und GOAe-Auszug stimmen mit submissions/ ueberein."
  fi
else
  echo "Abgleich fehlgeschlagen, siehe oben." >&2
fi
exit "$failures"
