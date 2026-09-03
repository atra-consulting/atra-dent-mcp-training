#!/usr/bin/env bash

fingerprint_of() {
  local tool
  if command -v sha256sum >/dev/null 2>&1; then
    tool="sha256sum"
  elif command -v shasum >/dev/null 2>&1; then
    tool="shasum -a 256"
  else
    echo "kein-hash-werkzeug"
    return 0
  fi
  # shellcheck disable=SC2086  # $tool is deliberately multi-word (shasum -a 256)
  find "$@" -type f ! -name '.DS_Store' ! -name '.seed' -print0 \
    | LC_ALL=C sort -z \
    | xargs -0 $tool \
    | $tool \
    | cut -d' ' -f1
}

compute_status() {
  fingerprint_of wissen/dokumente wissen/daten
}
