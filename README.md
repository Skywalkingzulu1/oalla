# Oalla

Run Ollama and any open language models directly on Android devices.

## What This Is

Oalla demonstrates running a complete Go web server inside an Android app process. The result is a mobile app that can run any Ollama-compatible model locally without internet connectivity.

This is completely open source, just like Ollama itself. You can use any models from Ollama's library or Hugging Face that work with the GGUF format.

## Architecture

```
JavaScript UI ←→ HTTP API ←→ Go Server (Ollama)
     ↓                           ↓
Android WebView              JNI Bridge
     ↓                           ↓
    Same Android Process
```

The app loads Ollama's web interface in a WebView while running the actual Ollama server natively in the same process. JavaScript communicates with the Go backend via standard HTTP requests to localhost.

## Technical Implementation

### [Converting Ollama for Android](external/ollama/README.md)

Step-by-step guide to modify the official Ollama repository for Android compatibility. Covers JNI bridge creation, in-process execution, cross-compilation, and the web API endpoints that make this possible.

### [Android Integration Details](android/README.md)

How the Android app manages the Go server lifecycle, handles JavaScript-native communication, implements security through dynamic ports and authentication, and manages encrypted assets.

## Models

Works with any Ollama model or GGUF-format models from Hugging Face:

- Llama models (3.1, 3.2, etc.)
- DeepSeek-R1 series
- Qwen models
- Mistral family
- Any quantized model in GGUF format

## Requirements

- Android 7.0+
- ARM64 device
- 4GB+ RAM for larger models
- Storage space for models (1-8GB each)

## Building

```bash
git clone https://github.com/isdzulqor/oalla.git
cd oalla
git submodule update --init --recursive

# Build Ollama for Android
cd external/ollama
./build_android.sh

# Build Android app
cd ../../android
./gradlew assembleRelease
```

## Why This Approach

This architecture proves that mobile devices can run sophisticated AI workloads locally. It maintains full compatibility with Ollama's ecosystem while providing a rich web-based interface that would be difficult to implement natively.

The approach is entirely offline-first and privacy-focused - no data leaves your device, no accounts required, no tracking.

## License

MIT License, same as Ollama. This project builds upon Ollama's work to bring it to mobile platforms.
