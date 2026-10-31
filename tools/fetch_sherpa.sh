#!/usr/bin/env bash
set -u
V="1.12.14"
BASE="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${V}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIBS="$ROOT/app/libs"
mkdir -p "$LIBS"
T="$(mktemp -d)"
ok_aar() { [ -s "$1" ] && unzip -l "$1" 2>/dev/null | grep -q "classes.jar" && unzip -l "$1" 2>/dev/null | grep -q "\.so"; }
echo "Plan A: AAR download"
for name in "sherpa-onnx-${V}.aar" "sherpa-onnx-v${V}.aar"; do
  if curl -fL --retry 3 -o "$T/a.aar" "$BASE/$name" 2>/dev/null && ok_aar "$T/a.aar"; then
    cp "$T/a.aar" "$LIBS/sherpa-onnx.aar"
    echo "OK: AAR mila ($name)"; exit 0
  fi
done
echo "Plan B: android tar + kotlin-api source"
curl -fL --retry 3 -o "$T/and.tar.bz2" "$BASE/sherpa-onnx-v${V}-android.tar.bz2" || { echo "FAIL: android tar nahi mila"; exit 1; }
mkdir "$T/and" && tar xjf "$T/and.tar.bz2" -C "$T/and"
JNI="$(find "$T/and" -type d -name jniLibs | head -1)"
[ -n "$JNI" ] || { echo "FAIL: tar me jniLibs nahi"; exit 1; }
rm -rf "$ROOT/app/src/main/jniLibs"; mkdir -p "$ROOT/app/src/main/jniLibs"
for abi in arm64-v8a x86_64; do
  [ -d "$JNI/$abi" ] && cp -r "$JNI/$abi" "$ROOT/app/src/main/jniLibs/$abi"
done
ls "$ROOT/app/src/main/jniLibs"/*/ >/dev/null 2>&1 || { echo "FAIL: .so copy nahi hui"; exit 1; }
curl -fL --retry 3 -o "$T/src.tgz" "https://github.com/k2-fsa/sherpa-onnx/archive/refs/tags/v${V}.tar.gz" || { echo "FAIL: source nahi mila"; exit 1; }
mkdir "$T/src" && tar xzf "$T/src.tgz" -C "$T/src"
K="$(find "$T/src" -type d -name kotlin-api | head -1)"
[ -n "$K" ] || { echo "FAIL: kotlin-api folder nahi mila"; exit 1; }
D="$ROOT/app/src/main/java/com/k2fsa/sherpa/onnx"
rm -rf "$D"; mkdir -p "$D"
cp "$K"/*.kt "$D"/
echo "OK: Plan B poora ($(ls "$D" | wc -l) kotlin files)"
