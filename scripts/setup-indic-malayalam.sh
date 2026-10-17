
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_DIR="$ROOT_DIR/.local-models/indic_ml"

BASE_URL="https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main"

MODEL_SHA="dcbdfa9f773db910508b40b703cb76c5974e8d4c6f123ea81265b40853c3f0c2"
TOKENS_SHA="ee60967630213f31951817ac8b402b92ec18cce80718a24a49b388e56672dfb2"

mkdir -p "$MODEL_DIR"

TEMP_FILE=""

cleanup() {
    if [[ -n "$TEMP_FILE" ]]; then
        rm -f "$TEMP_FILE"
    fi
}

trap cleanup EXIT

verify_checksum() {
    local file="$1"
    local expected="$2"
    local actual

    actual="$(shasum -a 256 "$file" | awk '{print $1}')"

    [[ "$actual" == "$expected" ]]
}

setup_file() {
    local name="$1"
    local url="$2"
    local expected_sha="$3"
    local target="$MODEL_DIR/$name"

    if [[ -f "$target" ]]; then
        if verify_checksum "$target" "$expected_sha"; then
            echo "INDIC SETUP PASSED: $name already verified"
            return 0
        fi

        echo "INDIC SETUP FAILED: Existing $name checksum mismatch" >&2
        return 1
    fi

    TEMP_FILE="$(mktemp "$MODEL_DIR/.${name}.XXXXXX")"

    echo "Downloading $name..."

    if ! curl --fail --show-error --location \
        --retry 3 \
        --connect-timeout 30 \
        --output "$TEMP_FILE" \
        "$url"; then
        echo "INDIC SETUP FAILED: Download error for $name" >&2
        return 1
    fi

    if ! verify_checksum "$TEMP_FILE" "$expected_sha"; then
        echo "INDIC SETUP FAILED: Checksum mismatch for $name" >&2
        return 1
    fi

    chmod 644 "$TEMP_FILE"
    mv "$TEMP_FILE" "$target"
    TEMP_FILE=""

    echo "INDIC SETUP PASSED: $name downloaded and verified"
}

setup_file \
    "model.int8.onnx" \
    "$BASE_URL/ml/model.int8.onnx" \
    "$MODEL_SHA"

setup_file \
    "tokens.txt" \
    "$BASE_URL/tokens.txt" \
    "$TOKENS_SHA"

echo "INDIC MALAYALAM SETUP COMPLETE"
