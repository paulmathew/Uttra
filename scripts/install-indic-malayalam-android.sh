
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_DIR="$ROOT_DIR/.local-models/indic_ml"
PACKAGE="com.uttra.app"

MODEL_SHA="dcbdfa9f773db910508b40b703cb76c5974e8d4c6f123ea81265b40853c3f0c2"
TOKENS_SHA="ee60967630213f31951817ac8b402b92ec18cce80718a24a49b388e56672dfb2"

ADB=(adb)

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    ADB+=(-s "$ANDROID_SERIAL")
fi

expected_sha() {
    case "$1" in
        model.int8.onnx) echo "$MODEL_SHA" ;;
        tokens.txt) echo "$TOKENS_SHA" ;;
        *) return 1 ;;
    esac
}

# Verify local files before accessing the device.
for name in model.int8.onnx tokens.txt; do
    source_file="$MODEL_DIR/$name"

    if [[ ! -f "$source_file" ]]; then
        echo "INDIC DEVICE FAILED: Missing local $name" >&2
        exit 1
    fi

    actual="$(shasum -a 256 "$source_file" | awk '{print $1}')"

    if [[ "$actual" != "$(expected_sha "$name")" ]]; then
        echo "INDIC DEVICE FAILED: Local checksum mismatch: $name" >&2
        exit 1
    fi
done

# Requires one authorized Android device, or ANDROID_SERIAL.
if [[ "$("${ADB[@]}" get-state)" != "device" ]]; then
    echo "INDIC DEVICE FAILED: Android device unavailable" >&2
    exit 1
fi

# run-as requires an installed, debuggable Uttra build.
if ! "${ADB[@]}" shell run-as "$PACKAGE" pwd >/dev/null; then
    echo "INDIC DEVICE FAILED: Install Uttra debug APK first" >&2
    exit 1
fi

# Check both installed files before modifying anything.
missing=()

for name in model.int8.onnx tokens.txt; do
    remote_file="files/indic_ml/$name"

    if "${ADB[@]}" shell run-as "$PACKAGE" \
        test -f "$remote_file" >/dev/null 2>&1; then

        actual="$("${ADB[@]}" shell run-as "$PACKAGE" \
            sha256sum "$remote_file" | awk '{print $1}')"

        if [[ "$actual" != "$(expected_sha "$name")" ]]; then
            echo "INDIC DEVICE FAILED: Existing model differs: $name" >&2
            exit 1
        fi

        echo "INDIC DEVICE PASSED: $name already verified"
    else
        missing+=("$name")
    fi
done

if [[ ${#missing[@]} -eq 0 ]]; then
    echo "INDIC DEVICE SETUP COMPLETE: Nothing to install"
    exit 0
fi

"${ADB[@]}" shell run-as "$PACKAGE" mkdir -p files/indic_ml

# Temporary staging area, removed when the script exits.
TEMP_DIR="$("${ADB[@]}" shell \
    mktemp -d /data/local/tmp/uttra-indic.XXXXXX | tr -d '\r')"

cleanup() {
    if [[ -n "${TEMP_DIR:-}" ]]; then
        "${ADB[@]}" shell rm -rf "$TEMP_DIR" >/dev/null || true
    fi
}

trap cleanup EXIT

"${ADB[@]}" shell chmod 755 "$TEMP_DIR"

for name in "${missing[@]}"; do
    echo "INDIC DEVICE: Installing $name"

    "${ADB[@]}" push \
        "$MODEL_DIR/$name" \
        "$TEMP_DIR/$name"

    "${ADB[@]}" shell chmod 644 "$TEMP_DIR/$name"

    staging_file="files/indic_ml/.$name.uttra-setup.tmp"
    destination="files/indic_ml/$name"

    "${ADB[@]}" shell run-as "$PACKAGE" \
        cp "$TEMP_DIR/$name" "$staging_file"

    actual="$("${ADB[@]}" shell run-as "$PACKAGE" \
        sha256sum "$staging_file" | awk '{print $1}')"

    if [[ "$actual" != "$(expected_sha "$name")" ]]; then
        echo "INDIC DEVICE FAILED: Device copy checksum mismatch" >&2
        exit 1
    fi

    "${ADB[@]}" shell run-as "$PACKAGE" \
        mv "$staging_file" "$destination"

    echo "INDIC DEVICE PASSED: $name installed and verified"
done

echo "INDIC DEVICE SETUP COMPLETE"
