#!/usr/bin/env bash
# build.sh — Compile, package, and optionally test ocr-processor
#
# Usage:
#   ./build.sh              # compile + package (tests skipped)
#   ./build.sh --test       # compile + run all Java tests
#   ./build.sh --test-unit  # run unit tests only (no Testcontainers)
#   ./build.sh --clean      # clean target/ before building
#   ./build.sh --docker     # build Docker image after packaging
#   ./build.sh --clean --test --docker   # combine flags

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
RUN_TESTS=false
UNIT_ONLY=false
CLEAN=false
BUILD_DOCKER=false

for arg in "$@"; do
  case $arg in
    --test)       RUN_TESTS=true ;;
    --test-unit)  RUN_TESTS=true; UNIT_ONLY=true ;;
    --clean)      CLEAN=true ;;
    --docker)     BUILD_DOCKER=true ;;
  esac
done

# ─── banner ───────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}╔═════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}║    ocr-processor  ·  build              ║${RESET}"
echo -e "${BOLD}╚═════════════════════════════════════════╝${RESET}"
echo ""

BUILD_START=$(date +%s)

# ─── load .env ────────────────────────────────────────────────────────────────
if [ -f .env ]; then
  set -o allexport
  # shellcheck disable=SC1091
  source ./.env
  set +o allexport
fi
: "${LLM_API_KEY:=dummy-key-for-dev}"
: "${JWT_SECRET:=dev-secret-key-must-be-at-least-32-characters-long}"
export LLM_API_KEY JWT_SECRET

# ─── step 1: verify java + maven ──────────────────────────────────────────────
header "Step 1: Toolchain"

if ! command -v java &>/dev/null; then
  error "java not found — run setup.sh first"
  exit 1
fi
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
      export PATH="$(dirname "$MVN_BIN"):$PATH"
      break
    fi
  done
fi

if [ -z "$MVN_BIN" ]; then
  error "mvn not found — run setup.sh first, or install Maven:"
  echo "    macOS  : brew install maven"
  echo "    Linux  : sudo apt-get install maven"
  echo "    SDKMAN : sdk install maven"
  exit 1
fi

JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
MVN_VER=$("$MVN_BIN" -q --version 2>&1 | head -1)
success "Java ${JAVA_VER} / $MVN_VER"

# ─── step 2: clean ────────────────────────────────────────────────────────────
if [ "$CLEAN" = true ]; then
  header "Step 2: Clean"
  info "Removing target/ …"
  mvn clean -q
  success "Clean complete"
else
  header "Step 2: Clean"
  info "Skipping clean (pass --clean to enable)"
fi

# ─── step 3: compile ──────────────────────────────────────────────────────────
header "Step 3: Compile"
info "Running: mvn compile"
mvn compile -q
success "Compilation successful"

# ─── step 4: tests ────────────────────────────────────────────────────────────
header "Step 4: Tests"

if [ "$RUN_TESTS" = false ]; then
  warn "Tests skipped — pass --test to run all tests, --test-unit for unit tests only"
else
  mkdir -p logs

  if [ "$UNIT_ONLY" = true ]; then
    info "Running unit tests only (no Testcontainers) …"
    # Unit tests live under src/test/java/**/unit/
    mvn test \
      -Dtest="**/unit/**/*Test" \
      -Dspring.profiles.active=test \
      2>&1 | tee logs/test-unit.log
    UNIT_RESULT=${PIPESTATUS[0]}

    if [ "$UNIT_RESULT" -eq 0 ]; then
      success "Unit tests passed"
    else
      error "Unit tests FAILED — see logs/test-unit.log"
      exit 1
    fi
  else
    info "Running all Java tests (unit + integration via Testcontainers) …"
    info "Note: Docker must be running for Testcontainers integration tests"
    mvn test \
      -Dspring.profiles.active=test \
      2>&1 | tee logs/test-all.log
    TEST_RESULT=${PIPESTATUS[0]}

    # Print summary line from Surefire
    echo ""
    grep -E "Tests run:|BUILD" logs/test-all.log | tail -5 || true

    if [ "$TEST_RESULT" -eq 0 ]; then
      success "All Java tests passed"
    else
      error "Tests FAILED — see logs/test-all.log"
      echo ""
      echo "  Failed tests:"
      grep -E "FAILED|ERROR" logs/test-all.log | grep -v "^\\[" | head -20 || true
      exit 1
    fi
  fi
fi

# ─── step 5: package ──────────────────────────────────────────────────────────
header "Step 5: Package"
info "Running: mvn package -DskipTests"
mvn package -DskipTests -q

JAR=$(find target -maxdepth 1 -name "ocr-processor-*.jar" ! -name "*sources*" 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  error "JAR not found in target/ after packaging"
  exit 1
fi

JAR_SIZE=$(du -sh "$JAR" | cut -f1)
success "Package: $JAR  ($JAR_SIZE)"

# ─── step 6: docker image (optional) ─────────────────────────────────────────
if [ "$BUILD_DOCKER" = true ]; then
  header "Step 6: Docker Image"

  if ! command -v docker &>/dev/null; then
    error "Docker not found — cannot build image"
    exit 1
  fi

  IMAGE_TAG="ocr-processor:latest"
  info "Building Docker image: $IMAGE_TAG"
  docker build -t "$IMAGE_TAG" .
  success "Docker image built: $IMAGE_TAG"
  info "Run:   docker run -p 8080:8080 --env-file .env $IMAGE_TAG"
else
  header "Step 6: Docker Image"
  info "Skipping Docker image build (pass --docker to enable)"
fi

# ─── done ─────────────────────────────────────────────────────────────────────
BUILD_END=$(date +%s)
ELAPSED=$((BUILD_END - BUILD_START))

echo ""
echo -e "${GREEN}${BOLD}╔══════════════════════════════════════════════╗${RESET}"
echo -e "${GREEN}${BOLD}║  Build complete!  (${ELAPSED}s)                       ║${RESET}"
echo -e "${GREEN}${BOLD}╚══════════════════════════════════════════════╝${RESET}"
echo ""
echo -e "  ${BOLD}JAR:${RESET}      $JAR"
echo -e "  ${BOLD}Next:${RESET}     ./start.sh"
echo ""
