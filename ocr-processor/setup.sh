#!/usr/bin/env bash
# setup.sh — Prepare the environment for ocr-processor
# Checks prerequisites, creates .env, and provisions the PostgreSQL database.
# Does NOT build or start the application (see build.sh and start.sh).
#
# Usage:
#   ./setup.sh              # full setup
#   ./setup.sh --skip-db    # skip database creation

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
SKIP_DB=false
for arg in "$@"; do
  [[ "$arg" == "--skip-db" ]] && SKIP_DB=true
done

# ─── banner ───────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}╔═════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}║    ocr-processor  ·  setup              ║${RESET}"
echo -e "${BOLD}╚═════════════════════════════════════════╝${RESET}"
echo ""

# ─── step 1: prerequisites ────────────────────────────────────────────────────
header "Step 1: Prerequisites"

MISSING=()

# Java 21+
if command -v java &>/dev/null; then
  JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
  if [ "${JAVA_VER:-0}" -lt 21 ]; then
    MISSING+=("Java 21+ (found Java ${JAVA_VER})")
  else
    success "Java ${JAVA_VER}"
  fi
else
  MISSING+=("java (not found)")
fi

# Maven — check PATH, then common install locations, then offer to install
MVN_BIN=""
if command -v mvn &>/dev/null; then
  MVN_BIN="mvn"
else
  for candidate in \
      "$HOME/.sdkman/candidates/maven/current/bin/mvn" \
      "/opt/homebrew/opt/maven/bin/mvn" \
      "/usr/local/opt/maven/bin/mvn" \
      "/usr/share/maven/bin/mvn" \
      "/opt/maven/bin/mvn"; do
    if [ -x "$candidate" ]; then
      MVN_BIN="$candidate"
      break
    fi
  done
fi

if [ -n "$MVN_BIN" ]; then
  MVN_VER=$("$MVN_BIN" -q --version 2>&1 | head -1)
  success "Maven — $MVN_VER"
  export PATH="$(dirname "$MVN_BIN"):$PATH"
else
  warn "Maven not found — attempting automatic install …"
  MAVEN_INSTALLED=false

  # Try Homebrew (macOS / Linux)
  if command -v brew &>/dev/null; then
    info "Installing Maven via Homebrew …"
    brew install maven
    command -v mvn &>/dev/null && MVN_BIN="mvn" && MAVEN_INSTALLED=true
  fi

  # Try apt-get (Debian / Ubuntu)
  if [ "$MAVEN_INSTALLED" = false ] && command -v apt-get &>/dev/null; then
    info "Installing Maven via apt-get …"
    sudo apt-get update -q && sudo apt-get install -y -q maven
    command -v mvn &>/dev/null && MVN_BIN="mvn" && MAVEN_INSTALLED=true
  fi

  # Try dnf (Fedora / RHEL)
  if [ "$MAVEN_INSTALLED" = false ] && command -v dnf &>/dev/null; then
    info "Installing Maven via dnf …"
    sudo dnf install -y -q maven
    command -v mvn &>/dev/null && MVN_BIN="mvn" && MAVEN_INSTALLED=true
  fi

  # Fallback: download Maven 3.9.9 tarball directly
  if [ "$MAVEN_INSTALLED" = false ]; then
    MAVEN_VERSION="3.9.9"
    MAVEN_DIR="$HOME/.local/maven"
    MAVEN_URL="https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
    info "Downloading Maven ${MAVEN_VERSION} to $MAVEN_DIR …"
    mkdir -p "$MAVEN_DIR"
    if command -v curl &>/dev/null; then
      curl -fsSL "$MAVEN_URL" | tar -xz -C "$MAVEN_DIR" --strip-components=1
    elif command -v wget &>/dev/null; then
      wget -qO- "$MAVEN_URL" | tar -xz -C "$MAVEN_DIR" --strip-components=1
    else
      error "No curl or wget found — cannot download Maven"
      MISSING+=("maven — install manually: https://maven.apache.org/install.html")
    fi
    if [ -x "$MAVEN_DIR/bin/mvn" ]; then
      MVN_BIN="$MAVEN_DIR/bin/mvn"
      MAVEN_INSTALLED=true
      # Persist to shell profile
      SHELL_RC=""
      [ -f "$HOME/.zshrc" ]    && SHELL_RC="$HOME/.zshrc"
      [ -f "$HOME/.bashrc" ]   && SHELL_RC="$HOME/.bashrc"
      if [ -n "$SHELL_RC" ]; then
        grep -q "maven" "$SHELL_RC" 2>/dev/null || \
          echo "export PATH=\"$MAVEN_DIR/bin:\$PATH\"" >> "$SHELL_RC"
        info "Added Maven to $SHELL_RC — run: source $SHELL_RC"
      fi
    fi
  fi

  if [ "$MAVEN_INSTALLED" = true ] && [ -n "$MVN_BIN" ]; then
    export PATH="$(dirname "$MVN_BIN"):$PATH"
    MVN_VER=$("$MVN_BIN" -q --version 2>&1 | head -1)
    success "Maven installed — $MVN_VER"
  else
    MISSING+=("maven — install manually: https://maven.apache.org/install.html")
  fi
fi

# PostgreSQL client
if command -v psql &>/dev/null; then
  success "psql $(psql --version 2>&1 | awk '{print $3}')"
else
  MISSING+=("psql (postgresql-client)")
fi

# Tesseract (optional but needed for OCR)
if command -v tesseract &>/dev/null; then
  TESS_VER=$(tesseract --version 2>&1 | head -1)
  success "Tesseract — $TESS_VER"
else
  warn "Tesseract not installed — OCR from images will fail at runtime"
  echo "       Linux : sudo apt-get install tesseract-ocr"
  echo "       macOS : brew install tesseract"
fi

# Python (optional, for E2E tests)
if command -v python3 &>/dev/null; then
  success "Python $(python3 --version 2>&1 | awk '{print $2}')"
else
  warn "Python 3 not found — E2E pytest tests will not run"
fi

# Docker (optional)
if command -v docker &>/dev/null; then
  success "Docker $(docker --version 2>&1 | awk '{print $3}' | tr -d ',')"
else
  warn "Docker not found — docker-compose workflow unavailable"
fi

if [ ${#MISSING[@]} -gt 0 ]; then
  echo ""
  error "Could not satisfy all required tools:"
  for t in "${MISSING[@]}"; do
    echo "    ✗  $t"
  done
  echo ""
  exit 1
fi

# ─── step 2: .env file ────────────────────────────────────────────────────────
header "Step 2: Environment file"

if [ -f .env ]; then
  success ".env already exists — skipping creation"
else
  if [ -f .env.example ]; then
    cp .env.example .env
    success "Created .env from .env.example"
    warn "Review .env and set real values before running the app:"
    echo ""
    echo "    Required:"
    echo "      JWT_SECRET    — at least 32 random characters"
    echo "      LLM_API_KEY   — your OpenAI (or other provider) API key"
    echo ""
    echo "    Optional (defaults are fine for local dev):"
    echo "      SPRING_DATASOURCE_URL / USERNAME / PASSWORD"
    echo "      SERVER_PORT   (default 8080)"
    echo ""
  else
    warn ".env.example not found — creating a minimal .env with dev defaults"
    cat > .env <<'ENVEOF'
# ocr-processor environment — edit before use
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ocrprocessor
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
JWT_SECRET=dev-secret-key-must-be-at-least-32-characters-long
LLM_API_KEY=dummy-key-for-dev
SERVER_PORT=8080
ENVEOF
    success "Created minimal .env"
  fi
fi

# Load .env. Source the file directly so values override any placeholder
# already exported in the shell. Piping through process substitution does not.
set -o allexport
# shellcheck disable=SC1091
source ./.env
set +o allexport

# Apply defaults
: "${SPRING_DATASOURCE_URL:=jdbc:postgresql://localhost:5432/ocrprocessor}"
: "${SPRING_DATASOURCE_USERNAME:=postgres}"
: "${SPRING_DATASOURCE_PASSWORD:=postgres}"
: "${JWT_SECRET:=dev-secret-key-must-be-at-least-32-characters-long}"
: "${LLM_API_KEY:=dummy-key-for-dev}"
: "${SERVER_PORT:=8080}"

info "DATASOURCE : $SPRING_DATASOURCE_URL"
info "APP PORT   : $SERVER_PORT"

if [ "$LLM_API_KEY" = "dummy-key-for-dev" ]; then
  warn "LLM_API_KEY is a placeholder — LLM summarisation will fail gracefully"
fi
if [ "${#JWT_SECRET}" -lt 32 ]; then
  error "JWT_SECRET must be at least 32 characters"
  exit 1
fi

# ─── step 3: Python dependencies ──────────────────────────────────────────────
header "Step 3: Python E2E dependencies"

VENV_DIR="tests/.venv"

if command -v python3 &>/dev/null && [ -f tests/requirements.txt ]; then
  # Create venv if it doesn't exist yet
  if [ ! -d "$VENV_DIR" ]; then
    info "Creating Python virtual environment at $VENV_DIR …"
    python3 -m venv "$VENV_DIR"
    success "Virtual environment created"
  else
    success "Virtual environment already exists ($VENV_DIR)"
  fi

  # Activate and check / install deps
  # shellcheck disable=SC1091
  source "$VENV_DIR/bin/activate"

  if python3 -c "import pytest, requests" &>/dev/null; then
    success "pytest and requests already installed in venv"
  else
    info "Installing Python test dependencies into venv …"
    pip install -q --upgrade pip
    pip install -q -r tests/requirements.txt
    success "Python dependencies installed"
  fi

  deactivate

  info "To activate the venv manually: source $VENV_DIR/bin/activate"
  info "E2E tests: source $VENV_DIR/bin/activate && pytest tests/e2e/ --base-url=http://localhost:8080"
else
  warn "Skipping Python deps (python3 or tests/requirements.txt not found)"
fi

# ─── step 4: database ─────────────────────────────────────────────────────────
header "Step 4: Database"

if [ "$SKIP_DB" = true ]; then
  warn "Skipping database setup (--skip-db)"
else
  # Parse connection details from JDBC URL
  # jdbc:postgresql://host:port/dbname
  DB_HOST=$(echo "$SPRING_DATASOURCE_URL" | sed -E 's|.*://([^:/]+).*|\1|')
  DB_PORT=$(echo "$SPRING_DATASOURCE_URL" | sed -E 's|.*:([0-9]+)/.*|\1|')
  DB_NAME=$(echo "$SPRING_DATASOURCE_URL" | sed -E 's|.*/([^?]+).*|\1|')

  : "${DB_HOST:=localhost}"
  : "${DB_PORT:=5432}"
  : "${DB_NAME:=ocrprocessor}"

  DOCKER_CONTAINER_NAME="ocr-processor-postgres"

  # ── check if PostgreSQL is already fully ready ──────────────────────────────
  info "Checking PostgreSQL at $DB_HOST:$DB_PORT …"
  PG_READY=false
  if command -v pg_isready &>/dev/null; then
    PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" pg_isready \
      -h "$DB_HOST" -p "$DB_PORT" \
      -U "$SPRING_DATASOURCE_USERNAME" -q 2>/dev/null && PG_READY=true
  else
    PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql \
      -h "$DB_HOST" -p "$DB_PORT" \
      -U "$SPRING_DATASOURCE_USERNAME" \
      -c "SELECT 1" -o /dev/null 2>/dev/null && PG_READY=true
  fi

  # ── if not reachable, try to start via Docker ───────────────────────────────
  if [ "$PG_READY" = false ]; then
    if command -v docker &>/dev/null; then
      # Check if our container already exists (stopped)
      if docker ps -a --format '{{.Names}}' 2>/dev/null | grep -q "^${DOCKER_CONTAINER_NAME}$"; then
        info "Starting existing Docker container '$DOCKER_CONTAINER_NAME' …"
        docker start "$DOCKER_CONTAINER_NAME" >/dev/null
      else
        info "PostgreSQL not running — starting via Docker …"
        docker run -d \
          --name "$DOCKER_CONTAINER_NAME" \
          -e POSTGRES_DB="$DB_NAME" \
          -e POSTGRES_USER="$SPRING_DATASOURCE_USERNAME" \
          -e POSTGRES_PASSWORD="$SPRING_DATASOURCE_PASSWORD" \
          -p "${DB_PORT}:5432" \
          --restart unless-stopped \
          postgres:16 >/dev/null
        success "Container '$DOCKER_CONTAINER_NAME' started"
      fi

      # Wait up to 60s for PostgreSQL to be fully ready (not just TCP open)
      info "Waiting for PostgreSQL to be ready …"
      for i in $(seq 1 30); do
        # Prefer pg_isready (ships with postgresql-client) — tests the actual
        # server startup state, not just whether the port is open
        if command -v pg_isready &>/dev/null; then
          if PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" pg_isready \
              -h "$DB_HOST" -p "$DB_PORT" \
              -U "$SPRING_DATASOURCE_USERNAME" -q 2>/dev/null; then
            PG_READY=true; break
          fi
        else
          # Fallback: try a real psql connection (rejects if not ready)
          if PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql \
              -h "$DB_HOST" -p "$DB_PORT" \
              -U "$SPRING_DATASOURCE_USERNAME" \
              -c "SELECT 1" -o /dev/null 2>/dev/null; then
            PG_READY=true; break
          fi
        fi
        printf "."
        sleep 2
      done
      echo ""
    else
      warn "Docker not available — cannot start PostgreSQL automatically"
    fi
  fi

  if [ "$PG_READY" = false ]; then
    error "Cannot reach PostgreSQL at $DB_HOST:$DB_PORT"
    echo ""
    echo "  Options:"
    echo "    A) Start with Docker (recommended):"
    echo "       docker run -d --name $DOCKER_CONTAINER_NAME \\"
    echo "         -e POSTGRES_DB=$DB_NAME \\"
    echo "         -e POSTGRES_USER=$SPRING_DATASOURCE_USERNAME \\"
    echo "         -e POSTGRES_PASSWORD=$SPRING_DATASOURCE_PASSWORD \\"
    echo "         -p $DB_PORT:5432 postgres:16"
    echo ""
    echo "    B) macOS native:  brew services start postgresql@16"
    echo "    C) Linux native:  sudo systemctl start postgresql"
    echo ""
    exit 1
  fi
  success "PostgreSQL is reachable at $DB_HOST:$DB_PORT"

  # ── create database if it doesn't exist ────────────────────────────────────
  if PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql \
      -h "$DB_HOST" -p "$DB_PORT" \
      -U "$SPRING_DATASOURCE_USERNAME" \
      -lqt 2>/dev/null | cut -d'|' -f1 | grep -qw "$DB_NAME"; then
    success "Database '$DB_NAME' already exists"
  else
    info "Creating database '$DB_NAME' …"
    PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" createdb \
      -h "$DB_HOST" -p "$DB_PORT" \
      -U "$SPRING_DATASOURCE_USERNAME" \
      "$DB_NAME"
    success "Database '$DB_NAME' created"
  fi
  info "Flyway migrations will run automatically when the app starts"
fi

# ─── done ─────────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}${BOLD}╔══════════════════════════════════════════════╗${RESET}"
echo -e "${GREEN}${BOLD}║  Setup complete!                             ║${RESET}"
echo -e "${GREEN}${BOLD}╚══════════════════════════════════════════════╝${RESET}"
echo ""
echo "  Next steps:"
echo "    1. Edit .env if needed           (JWT_SECRET and LLM_API_KEY)"
echo "    2. Build the app:                ./build.sh"
echo "    3. Start the app:                ./start.sh"
echo ""
echo "  Run E2E tests (after app is running):"
echo "    source tests/.venv/bin/activate"
echo "    pytest tests/e2e/ --base-url=http://localhost:8080 -v"
echo "    deactivate"
echo ""
