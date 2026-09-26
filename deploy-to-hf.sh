#!/usr/bin/env bash
# deploy-to-hf.sh — Deploy ocr-processor to Hugging Face Spaces (Veeru-c/ocr-processor)
set -euo pipefail

SPACE_REPO="https://huggingface.co/spaces/Veeru-c/ocr-processor"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="/tmp/hf-ocr-deploy"

echo "============================================================"
echo " Deploying ocr-processor to Hugging Face Spaces"
echo " Space: ${SPACE_REPO}"
echo "============================================================"

# 1. Clone or pull existing deployment workspace
if [ ! -d "$DEPLOY_DIR/.git" ]; then
    rm -rf "$DEPLOY_DIR"
    echo "[1/4] Cloning Hugging Face Space repository..."
    git clone "$SPACE_REPO" "$DEPLOY_DIR"
else
    echo "[1/4] Pulling latest remote changes..."
    git -C "$DEPLOY_DIR" pull --rebase origin main || true
fi

# 2. Sync source files (excluding build artifacts and local secrets)
echo "[2/4] Synchronizing ocr-processor application files..."
rsync -av \
    --exclude="target" \
    --exclude="logs" \
    --exclude=".env" \
    --exclude=".pytest_cache" \
    --exclude="__pycache__" \
    --exclude="*.pyc" \
    --exclude="*.pid" \
    --exclude=".git" \
    "${SCRIPT_DIR}/ocr-processor/" "$DEPLOY_DIR/"

# 3. Stage changes
cd "$DEPLOY_DIR"
git add -A

if git diff --staged --quiet; then
    echo "[3/4] No changes detected. Space repository is already up to date."
    echo "Visit your Space: https://huggingface.co/spaces/Veeru-c/ocr-processor"
    exit 0
fi

# 4. Commit and push
COMMIT_MSG="Deploy ocr-processor update: $(date -u +"%Y-%m-%d %H:%M:%SZ")"
echo "[3/4] Creating commit: ${COMMIT_MSG}"
git commit -m "$COMMIT_MSG"

echo "[4/4] Pushing changes to Hugging Face Spaces..."
echo "If prompted for password, enter your Hugging Face User Access Token (with Write permission)."
git push origin main

echo "============================================================"
echo " Deployment successfully pushed to Hugging Face Spaces!"
echo " Monitor build and live app at: https://huggingface.co/spaces/Veeru-c/ocr-processor"
echo "============================================================"
