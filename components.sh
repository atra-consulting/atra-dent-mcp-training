#!/usr/bin/env bash


load_env() {
  [ -f "$root/.env" ] || return 0
  local line name
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      ''|'#'*) continue ;;
      [A-Za-z_]*=*) ;;
      *) continue ;;
    esac
    name="${line%%=*}"
    case "$name" in
      *[!A-Za-z0-9_]*) continue ;;
    esac
    [ -n "${!name+set}" ] && continue
    eval "export $line"
  done < "$root/.env"
}

load_env

COMPONENTS=(rechenkern kernsystem wissen agenten-beratung
           agenten-schadensfall agenten-orchestrator sachbearbeiter-ui kunden-chat)

KERNSYSTEM_PORT="${KERNSYSTEM_PORT:-8080}"
WISSEN_PORT="${WISSEN_PORT:-8082}"
AGENTEN_ORCHESTRATOR_PORT="${AGENTEN_ORCHESTRATOR_PORT:-8084}"
AGENTEN_BERATUNG_PORT="${AGENTEN_BERATUNG_PORT:-8085}"
RECHENKERN_PORT="${RECHENKERN_PORT:-8086}"
AGENTEN_SCHADENSFALL_PORT="${AGENTEN_SCHADENSFALL_PORT:-8087}"
SACHBEARBEITER_UI_PORT="${SACHBEARBEITER_UI_PORT:-5173}"
KUNDEN_CHAT_PORT="${KUNDEN_CHAT_PORT:-5174}"

DEBUG_HOST="${DEBUG_HOST:-127.0.0.1}"

KERNSYSTEM_DEBUG_PORT="${KERNSYSTEM_DEBUG_PORT:-$((KERNSYSTEM_PORT + 1000))}"
WISSEN_DEBUG_PORT="${WISSEN_DEBUG_PORT:-$((WISSEN_PORT + 1000))}"
AGENTEN_ORCHESTRATOR_DEBUG_PORT="${AGENTEN_ORCHESTRATOR_DEBUG_PORT:-$((AGENTEN_ORCHESTRATOR_PORT + 1000))}"
AGENTEN_BERATUNG_DEBUG_PORT="${AGENTEN_BERATUNG_DEBUG_PORT:-$((AGENTEN_BERATUNG_PORT + 1000))}"
RECHENKERN_DEBUG_PORT="${RECHENKERN_DEBUG_PORT:-$((RECHENKERN_PORT + 1000))}"
AGENTEN_SCHADENSFALL_DEBUG_PORT="${AGENTEN_SCHADENSFALL_DEBUG_PORT:-$((AGENTEN_SCHADENSFALL_PORT + 1000))}"
SACHBEARBEITER_UI_DEBUG_PORT="${SACHBEARBEITER_UI_DEBUG_PORT:-$((SACHBEARBEITER_UI_PORT + 1000))}"
KUNDEN_CHAT_DEBUG_PORT="${KUNDEN_CHAT_DEBUG_PORT:-$((KUNDEN_CHAT_PORT + 1000))}"

port_of() {
  case "$1" in
    rechenkern)           echo "$RECHENKERN_PORT" ;;
    kernsystem)           echo "$KERNSYSTEM_PORT" ;;
    wissen)               echo "$WISSEN_PORT" ;;
    agenten-orchestrator) echo "$AGENTEN_ORCHESTRATOR_PORT" ;;
    agenten-beratung)     echo "$AGENTEN_BERATUNG_PORT" ;;
    agenten-schadensfall) echo "$AGENTEN_SCHADENSFALL_PORT" ;;
    sachbearbeiter-ui)    echo "$SACHBEARBEITER_UI_PORT" ;;
    kunden-chat)          echo "$KUNDEN_CHAT_PORT" ;;
  esac
}

debug_port_of() {
  case "$1" in
    rechenkern)           echo "$RECHENKERN_DEBUG_PORT" ;;
    kernsystem)           echo "$KERNSYSTEM_DEBUG_PORT" ;;
    wissen)               echo "$WISSEN_DEBUG_PORT" ;;
    agenten-orchestrator) echo "$AGENTEN_ORCHESTRATOR_DEBUG_PORT" ;;
    agenten-beratung)     echo "$AGENTEN_BERATUNG_DEBUG_PORT" ;;
    agenten-schadensfall) echo "$AGENTEN_SCHADENSFALL_DEBUG_PORT" ;;
    sachbearbeiter-ui)    echo "$SACHBEARBEITER_UI_DEBUG_PORT" ;;
    kunden-chat)          echo "$KUNDEN_CHAT_DEBUG_PORT" ;;
  esac
}

runs_on_jvm() {
  case "$1" in
    sachbearbeiter-ui|kunden-chat) return 1 ;;
    *) return 0 ;;
  esac
}

is_component() {
  local candidate="$1" name
  for name in "${COMPONENTS[@]}"; do
    [ "$name" = "$candidate" ] && return 0
  done
  return 1
}
