
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

AAR_NAME="sherpa-onnx-1.13.8.aar"
TARGET="$ROOT_DIR/app/libs/$AAR_NAME"

URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/$AAR_NAME"

EXPECTED_SHA="633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"

mkdir -p "$(dirname "$TARGET")"

verify_checksum() {
    local actual
    actual="$(shasum -a 256 "$1" | awk '{print $1}')"
    [[ "$actual" == "$EXPECTED_SHA" ]]
}

if [[ -f "$TARGET" ]]; then
    if verify_checksum "$TARGET"; then
        echo "SHERPA SETUP PASSED: Existing AAR verified"
        exit 0
    fi

    echo "SHERPA SETUP FAILED: Existing AAR checksum mismatch" >&2
    exit 1
fi

TEMP_FILE="$(mktemp "$(dirname "$TARGET")/.sherpa-onnx.XXXXXX")"
trap 'rm -f "$TEMP_FILE"' EXIT

echo "Downloading sherpa-onnx 1.13.8..."

curl --fail --location \
    --retry 3 \
    --output "$TEMP_FILE" \
    "$URL"

if ! verify_checksum "$TEMP_FILE"; then
    echo "SHERPA SETUP FAILED: Download checksum mismatch" >&2
    exit 1
fi

mv "$TEMP_FILE" "$TARGET"

echo "SHERPA SETUP PASSED: AAR downloaded and verified"
