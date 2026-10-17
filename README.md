# Uttra (ഉത്ര)

Uttra is a native Android offline voice-dictation application built using Kotlin and Jetpack Compose.

## Current capabilities

- Offline English transcription using Whisper.cpp (Whisper Tiny)
- Offline Malayalam transcription using IndicConformer
- Floating microphone using an Android Accessibility Service
- Voice recording and transcription
- Cross-app text insertion, including cursor-aware insertion
- Editor validation and navigation safety checks

Automatic language detection, mixed-language code-switching, punctuation restoration, and live transcription are planned. The transcription router supports explicit English and Malayalam modes internally; the current recording flow uses Malayalam until language selection is implemented.

## Requirements

- macOS (the setup scripts have been tested on macOS; other operating systems are not yet verified)
- Android Studio
- Android SDK, NDK, and CMake versions required by the Gradle project
- A JDK compatible with the project's Android Gradle Plugin
- Git, `curl`, `shasum`, and Android Debug Bridge (`adb`)
- Internet access for the initial dependency and model downloads
- An Android device with USB debugging enabled
- Access to the private GitHub repository

## Set up from a fresh clone

### 1. Clone the repository

```bash
git clone --recurse-submodules https://github.com/paulmathew/Uttra.git
cd Uttra
```

If you cloned without submodules, run:

```bash
git submodule update --init --recursive
```

### 2. Download the sherpa-onnx dependency

```bash
bash scripts/setup-sherpa-onnx.sh
```

Downloads and checks the SHA-256 checksum of `sherpa-onnx-1.13.8.aar` in `app/libs/`.

### 3. Download Whisper models

```bash
bash scripts/setup-whisper-models.sh
```

Downloads and verifies Whisper Tiny and Base model files in:

```text
app/src/main/assets/models/
```

These binaries are excluded from Git.

### 4. Download the Malayalam IndicConformer model

```bash
bash scripts/setup-indic-malayalam.sh
```

Downloads and verifies `model.int8.onnx` and `tokens.txt` in:

```text
.local-models/indic_ml/
```

This directory is excluded from Git.

### 5. Build Uttra

Use Android Studio to install any SDK, NDK, or CMake components required by the Gradle project. Then run:

```bash
./gradlew :app:assembleDebug
```

### 6. Install the debug application

Connect and authorize your Android device, then run:

```bash
adb devices -l
./gradlew :app:installDebug
```

### 7. Provision the Malayalam model onto the device

```bash
bash scripts/install-indic-malayalam-android.sh
```

The script checks the model files and installs any missing verified files into Uttra's application-private `files/indic_ml/` directory. Existing matching files are kept; existing files with unexpected checksums cause the script to stop rather than overwrite them.

This mechanism requires an **installed, debuggable Uttra build** and uses `adb run-as`. It is for development setup, not production distribution. If more than one Android device is connected, set `ANDROID_SERIAL` to choose a device.

### 8. Enable Uttra and test dictation

Launch Uttra, grant microphone permission, and enable the Uttra Accessibility Service in Android Settings. Focus an editable text field in another app, use the floating microphone to record speech, and confirm to transcribe and insert the result.

Do not test message insertion by automatically sending messages; confirm the text appears in the intended editable field first.

## Development notes

- Stable development branch: `main`; create feature branches for new work.
- Speech recognition runs locally on the device after provisioning.
- Large speech models and the sherpa-onnx AAR are not stored in Git.
- Native accessibility `InputConnection` insertion is available on Android 13+ and was tested on a Pixel 7a. Older Android versions require separate compatibility validation.
- The current model-provisioning script supports **debug builds**; production distribution requires a separate model-installation strategy.
- Model downloads are pinned using SHA-256 checksums.

## Reproducibility status

**Verified on the development machine:**

- sherpa-onnx AAR download and SHA-256 verification
- Whisper model download and SHA-256 verification
- IndicConformer Malayalam model download and SHA-256 verification
- Android debug build
- Existing-device model verification (no-op when files match)
- Test-file provisioning through ADB into app-private storage

**Not yet verified:**

- End-to-end setup on a separate fresh laptop
- Full Malayalam model provisioning into a fresh app installation
- Production model delivery and installation
