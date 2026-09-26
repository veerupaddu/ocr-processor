#!/usr/bin/env bash
# start.sh — Start the ocr-processor application
#
# Usage:
#   ./start.sh              # start in background (dev profile)
#   ./start.sh --prod       # start with prod profile
#   ./start.sh --foreground # stream logs to stdout instead of log file
#   ./start.sh --port 9090  # override port

set -euo pipefail

# ─── colours ─────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

info()    { echo -e "${CYAN}[INFO]${RESET}  $*"; }
success() { echo -e "${GREEN}[ OK ]${RESET}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${RESET}  $*"; }
error()   { echo -e "${RED}[ERR ]${RESET}  $*" >&2; }
header()  { echo -e "\n${BOLD}━━━  $*  ━━━${RESET}"; }

# ─── flags ────────────────────────────────────────────────────────────────────
PROFILE=default
FOREGROUND=false
CUSTOM_PORT=""

i=1
while [ $i -le $# ]; do
  arg="${!i}"
  case $arg in
    --prod)       PROFILE=prod ;;
    --foreground) FOREGROUND=true ;;
    --port)       i=$((i+1)); CUSTOM_PORT="${!i}" ;;
  esac
  i=$((i+1))
done

# ─── banner ───────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}╔═════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}║    ocr-processor  ·  start              ║${RESET}"
echo -e "${BOLD}╚═════════════════════════════════════════╝${RESET}"
echo ""

# ─── load .env ────────────────────────────────────────────────────────────────
header "Environment"

if [ -f .env ]; then
  set -o allexport
  # shellcheck disable=SC1091
  source ./.env
  set +o allexport
  success "Loaded .env"
else
  warn ".env not found — using defaults (run setup.sh first)"
fi

: "${SPRING_DATASOURCE_URL:=jdbc:postgresql://localhost:5432/ocrprocessor}"
: "${SPRING_DATASOURCE_USERNAME:=postgres}"
: "${SPRING_DATASOURCE_PASSWORD:=postgres}"
: "${JWT_SECRET:=dev-secret-key-must-be-at-least-32-characters-long}"
: "${LLM_API_KEY:=dummy-key-for-dev}"
: "${SERVER_PORT:=8080}"

[ -n "$CUSTOM_PORT" ] && SERVER_PORT="$CUSTOM_PORT"

export SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
export JWT_SECRET LLM_API_KEY SERVER_PORT

info "Profile   : $PROFILE"
info "Port      : $SERVER_PORT"
info "Datasource: $SPRING_DATASOURCE_URL"

# ─── find JAR ─────────────────────────────────────────────────────────────────
header "Locating JAR"

JAR=$(find target -maxdepth 1 -name "ocr-processor-*.jar" ! -name "*sources*" 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  error "No JAR found in target/ — run ./build.sh first"
  exit 1
fi
success "JAR: $JAR"

# ─── stop existing instance ───────────────────────────────────────────────────
if [ -f logs/app.pid ]; then
  OLD_PID=$(cat logs/app.pid)
  if kill -0 "$OLD_PID" 2>/dev/null; then
    warn "Stopping existing instance (PID $OLD_PID) …"
    kill "$OLD_PID"
    sleep 2
    success "Stopped PID $OLD_PID"
  fi
  rm -f logs/app.pid
fi

# ─── start ────────────────────────────────────────────────────────────────────
header "Starting"

mkdir -p logs
LOG_FILE="logs/app.log"

JAVA_CMD=(
  java
  -jar "$JAR"
  "--spring.profiles.active=$PROFILE"
  "--server.port=$SERVER_PORT"
)

if [ "$FOREGROUND" = true ]; then
  info "Running in foreground — press Ctrl+C to stop"
  exec "${JAVA_CMD[@]}"
fi

# Background mode
"${JAVA_CMD[@]}" >> "$LOG_FILE" 2>&1 &
APP_PID=$!
echo "$APP_PID" > logs/app.pid
info "PID $APP_PID → $LOG_FILE"

# ─── health check ─────────────────────────────────────────────────────────────
header "Health check"

READY=false
for i in $(seq 1 40); do
  if curl -sf "http://localhost:${SERVER_PORT}/login" -o /dev/null 2>/dev/null; then
    READY=true
    break
  fi
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    error "Process $APP_PID died — check $LOG_FILE"
    echo ""
    tail -30 "$LOG_FILE"
    exit 1
  fi
  printf "."
  sleep 2
done
echo ""

if [ "$READY" = false ]; then
  error "App did not respond after 80s — check $LOG_FILE"
  tail -30 "$LOG_FILE"
  exit 1
fi

# ─── done ─────────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}${BOLD}╔══════════════════════════════════════════════════╗${RESET}"
echo -e "${GREEN}${BOLD}║  ocr-processor is running!                       ║${RESET}"
echo -e "${GREEN}${BOLD}╚══════════════════════════════════════════════════╝${RESET}"
echo ""
echo -e "  ${BOLD}URL   :${RESET}  http://localhost:${SERVER_PORT}"
echo -e "  ${BOLD}Logs  :${RESET}  tail -f $LOG_FILE"
echo -e "  ${BOLD}PID   :${RESET}  $APP_PID  (logs/app.pid)"
echo -e "  ${BOLD}Stop  :${RESET}  kill \$(cat logs/app.pid)"
echo -e "  ${BOLD}Restart: ${RESET}./start.sh"
echo ""
