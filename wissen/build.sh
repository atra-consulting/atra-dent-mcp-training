#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

./wissen/dokumente-build.sh
echo
./wissen/index-build.sh
echo

# shellcheck source=wissen/stand.sh
. ./wissen/stand.sh
compute_status > wissen/generated/stand.txt
echo "Stand der Quellen vermerkt: wissen/generated/stand.txt"
