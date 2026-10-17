
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_DIR="$ROOT_DIR/app/src/main/assets/models"

BASE_URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

TINY_SHA="be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"
BASE_SHA="60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe"

mkdir -p "$MODEL_DIR"

verify_checksum() {
    local file="$1"
    local expected="$2"
    local actual

    actual="$(shasum -a 256 "$file" | awk '{print $1}')"

    [[ "$actual" == "$expected" ]]
}

download_model() {
    local name="$1"
    local expected_sha="$2"
    local target="$MODEL_DIR/$name"

    if [[ -f "$target" ]]; then
        if verify_checksum "$target" "$expected_sha"; then
            echo "WHISPER SETUP PASSED: $name already verified"
            return 0
        fi

        echo "WHISPER SETUP FAILED: Existing $name checksum mismatch" >&2
        return 1
    fi

    local temp_file
    temp_file="$(mktemp "$MODEL_DIR/.${name}.XXXXXX")"

    echo "Downloading $name..."

    if ! curl --fail --location \
        --retry 3 \
        --connect-timeout 30 \
        --output "$temp_file" \
        "$BASE_URL/$name"; then

        rm -f "$temp_file"
        echo "WHISPER SETUP FAILED: Download error for $name" >&2
        return 1
    fi

    if ! verify_checksum "$temp_file" "$expected_sha"; then
        rm -f "$temp_file"
        echo "WHISPER SETUP FAILED: Checksum mismatch for $name" >&2
        return 1
    fi

    mv "$temp_file" "$target"

    echo "WHISPER SETUP PASSED: $name downloaded and verified"
}

download_model "ggml-tiny.bin" "$TINY_SHA"
download_model "ggml-base.bin" "$BASE_SHA"

echo "WHISPER SETUP COMPLETE"
