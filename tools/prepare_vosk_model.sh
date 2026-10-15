#!/usr/bin/env bash
set -euo pipefail

# Prepare the official small US-English Vosk model required by NovaService.loadModel().
# The model is intentionally fetched at build time rather than checked into the source ZIP.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets"
DEST="$ASSETS/model-en"
MODEL_URL="https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
TMP="$(mktemp -d)"
cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT

valid_model() {
  local d="$1"
  [[ -s "$d/am/final.mdl" \
     && -s "$d/graph/HCLr.fst" \
     && -s "$d/graph/Gr.fst" ]]
}

if valid_model "$DEST"; then
  echo "Vosk model already present and required files are valid."
  exit 0
fi

command -v curl >/dev/null 2>&1 || { echo "ERROR: curl is required to download the Vosk model." >&2; exit 2; }
command -v unzip >/dev/null 2>&1 || { echo "ERROR: unzip is required to extract the Vosk model." >&2; exit 2; }

echo "Downloading official Vosk small English model over HTTPS..."
curl --fail --location --silent --show-error --retry 3 --connect-timeout 20 \
  "$MODEL_URL" --output "$TMP/vosk-model.zip"
unzip -q "$TMP/vosk-model.zip" -d "$TMP/extracted"

SOURCE="$TMP/extracted/vosk-model-small-en-us-0.15"
if ! valid_model "$SOURCE"; then
  echo "ERROR: downloaded archive is missing required Vosk files; refusing to package it." >&2
  exit 3
fi

# Stage to a temporary directory under assets and only replace after validation.
STAGE="$ASSETS/.model-en-stage-$$"
rm -rf "$STAGE"
mkdir -p "$ASSETS"
cp -a "$SOURCE" "$STAGE"
if ! valid_model "$STAGE"; then
  rm -rf "$STAGE"
  echo "ERROR: staged Vosk model failed validation." >&2
  exit 4
fi
rm -rf "$DEST"
mv "$STAGE" "$DEST"
echo "Vosk model ready at app/src/main/assets/model-en."
