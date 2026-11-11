#!/usr/bin/env bash
# Builds one signed prod release APK per recruitment source. Each APK auto-enrols
# into the prod study (AUTO_ENROLMENT) and sends its source with the enrolment.
#
# Usage: KEYSTORE=/path/to/release.jks KEY_ALIAS=<alias> scripts/build-source-apks.sh
# Output: dist/<version>/whale-<version>-<source>.apk (+ R8 mapping per source),
#         <version> is versionName with dots as dashes, e.g. 1-0-45
#
# Reads (both gitignored, next to this script):
#   sources.txt     one source per line; blank lines and #-comments are skipped
#   keystore-pw.sh  prints the keystore password to stdout
set -euo pipefail

cd "$(dirname "$0")/.."

SOURCES=()
while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%%#*}"
  line="$(echo "$line" | tr -d '[:space:]')"
  [[ -z "$line" ]] && continue
  if [[ ! "$line" =~ ^[A-Za-z0-9_-]+$ ]]; then
    echo "invalid source in scripts/sources.txt: '$line' (allowed: A-Z a-z 0-9 _ -)" >&2
    exit 1
  fi
  SOURCES+=("$line")
done < scripts/sources.txt
[[ ${#SOURCES[@]} -gt 0 ]] || { echo "scripts/sources.txt has no sources" >&2; exit 1; }

: "${KEYSTORE:?set KEYSTORE to the release keystore path}"
: "${KEY_ALIAS:?set KEY_ALIAS to the release key alias}"

SDK_DIR=$(sed -n 's/^sdk.dir=//p' local.properties)
BUILD_TOOLS_VERSION=$(sed -n "s/.*android_version = '\(.*\)'/\1/p" build.gradle)
BUILD_TOOLS="$SDK_DIR/build-tools/$BUILD_TOOLS_VERSION"
VERSION_NAME=$(sed -n 's/.*versionName "\(.*\)"/\1/p' app/build.gradle)
VERSION_NAME="${VERSION_NAME//./-}"

WHALE_KS_PASS=$(bash scripts/keystore-pw.sh)
export WHALE_KS_PASS

echo "Building ${#SOURCES[@]} APKs: ${SOURCES[*]}"

OUT_DIR="dist/$VERSION_NAME"
mkdir -p "$OUT_DIR"

for source in "${SOURCES[@]}"; do
  echo "==> $source"
  ./gradlew --quiet assembleProdRelease -PenrolmentSource="$source"

  unsigned=app/build/outputs/apk/prod/release/app-prod-release-unsigned.apk
  target="$OUT_DIR/whale-$VERSION_NAME-$source.apk"

  "$BUILD_TOOLS/zipalign" -f -p 4 "$unsigned" "$target.aligned"
  "$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" --ks-pass env:WHALE_KS_PASS \
    --v4-signing-enabled false \
    --out "$target" "$target.aligned"
  rm "$target.aligned"

  cp app/build/outputs/mapping/prodRelease/mapping.txt "$OUT_DIR/mapping-$source.txt"
done

echo "Done: $OUT_DIR"
