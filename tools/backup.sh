#!/usr/bin/env bash
# v7.221 BACKUP (Part 5): poore project ka zip + sha256 + version stamp -> backups/ me.
# Usage: ./tools/backup.sh ["note"]
# Rollback ke steps: docs/BACKUP_ROLLBACK.md
set -e
cd "$(dirname "$0")/.."
NOTE="${1:-manual-backup}"
V=$(grep -o 'versionName = "[^"]*"' app/build.gradle.kts | head -1 | cut -d'"' -f2)
DAY=$(date +%Y-%m-%d_%H%M)
OUT="backups/JARVIS_v${V}_${DAY}.zip"
mkdir -p backups
# state/ DB aur runtime cheezein backup me NAHI jaati (sirf source + docs + website + workflows)
zip -qr "$OUT" . \
  -x "backups/*" -x ".git/*" -x "*/node_modules/*" -x "*/__pycache__/*" \
  -x "backend/state/brain.db*" -x "*.jks" -x "*.keystore" -x "local.properties" \
  -x "app/build/*" -x ".gradle/*" -x "*.ppn" -x "desktop/ui/*"
SHA=$(sha256sum "$OUT" | cut -d' ' -f1)
echo "${DAY} ${V} ${NOTE} sha256=${SHA}" >> backups/BACKUP_LEDGER.txt
echo "BACKUP READY: $OUT"
echo "sha256: $SHA"
echo "Ledger me likh diya (backups/BACKUP_LEDGER.txt)."
