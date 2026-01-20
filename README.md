<div align="center">
  <img src="assets/oala-logo.svg" alt="Oalla Logo" height="80"/>
  
  <p><strong>Run Ollama and open language models directly on Android devices</strong></p>
  
  <p>
    <a href="#what-this-is">What This Is</a> •
    <a href="#architecture">Architecture</a> •
    <a href="#technical-implementation">Technical Implementation</a> •
    <a href="#models">Models</a> •
    <a href="#why-this-approach">Why This Approach</a>
  </p>
</div>

---

## What This Is

Oalla demonstrates running a complete [Go](https://golang.org/) web server inside an Android app process. The result is a mobile app that can run any [Ollama](https://github.com/ollama/ollama)-compatible model locally without internet connectivity.

This is completely open source, just like [Ollama](https://github.com/ollama/ollama) itself. You can use any models from [Ollama's library](https://ollama.com/search) or [Hugging Face](https://huggingface.co/models?library=gguf) that work with the GGUF format.

<div align="center">
  <img src="assets/demo.gif" alt="Oalla Demo" width="280"/>
  <p><em>Oalla running locally on Android with offline AI models</em></p>
</div>

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Android App Process                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐    HTTP     ┌─────────────────────────┐    │
│  │   JavaScript    │ ←────────→  │     Go Server           │    │
│  │   Chat UI       │  localhost  │     (Ollama)            │    │
│  │                 │ :8000-8500  │                         │    │
│  └─────────────────┘  (dynamic)  └─────────────────────────┘    │
│           │                                    │                │
│           │                                    │                │
│  ┌─────────────────┐             ┌─────────────────────────┐    │
│  │  Android        │             │    JNI Bridge           │    │
│  │  WebView        │             │    (libbridgeollama.so) │    │
│  │                 │             │                         │    │
│  └─────────────────┘             └─────────────────────────┘    │
│           │                                    │                │
│           └────────────────────────────────────┘                │
│                    Native Integration                           │
└─────────────────────────────────────────────────────────────────┘
```

**Key Components:**

- **JavaScript UI**: Rich web-based chat interface running in [WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview)
- **HTTP API**: Standard REST endpoints (`/api/chat`, `/api/models`, etc.)
- **Go Server**: Full [Ollama](https://github.com/ollama/ollama) server compiled as Android native library
- **JNI Bridge**: Connects [Kotlin](https://kotlinlang.org/)/Java Android code with [Go](https://golang.org/) server
- **Single Process**: Everything runs in one Android app process for efficiency
- **Dynamic Port**: Randomly allocated port (8000-8500) for security

The app loads [Ollama's](https://github.com/ollama/ollama) web interface in a [WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview) while running the actual [Ollama](https://github.com/ollama/ollama) server natively in the same process. JavaScript communicates with the [Go](https://golang.org/) backend via standard HTTP requests to localhost.

## Technical Implementation

### [Converting Ollama for Android](external/ollama/README.md)

Step-by-step guide to modify the official [Ollama](https://github.com/ollama/ollama) repository for Android compatibility. Covers [JNI](https://developer.android.com/training/articles/perf-jni) bridge creation, in-process execution, cross-compilation, and the web API endpoints that make this possible.

### [Android Integration Details](android/README.md)

How the Android app manages the [Go](https://golang.org/) server lifecycle, handles JavaScript-native communication, implements security through dynamic ports and authentication, and manages encrypted assets.

## Models

Works with any [Ollama model](https://ollama.com/search) or [GGUF](https://github.com/ggerganov/ggml/blob/master/docs/gguf.md)-format models from [Hugging Face](https://huggingface.co/models?library=gguf):

### Tested [Ollama Models](https://ollama.com/search)

| Model | Size | Context | Type |
|-------|------|---------|------|
| `tinyllama:latest` | 638MB | 2K | Text |
| `qwen3:0.6b` | 523MB | 40K | Text |
| `smollm2:135m` | 135MB | 4K | Text |
| `gemma3:270m` | 292MB | 32k | Text |

### Tested [Hugging Face Models](https://huggingface.co/models?library=gguf)

| Model | Size | Context | Type |
|-------|------|---------|------|
| [`hf.co/unsloth/Qwen3-4B-GGUF:Q4_K_M`](https://huggingface.co/unsloth/Qwen3-4B-GGUF) | 1.03GB | 128K | Text |

## Why This Approach

This architecture proves that mobile devices can run sophisticated AI workloads locally. It maintains full compatibility with [Ollama's](https://github.com/ollama/ollama) ecosystem while providing a rich web-based interface that would be difficult to implement natively.

The approach is entirely offline-first and privacy-focused - no data leaves your device, no accounts required, no tracking.

**Benefits:**

- Easy model installation - just download [GGUF](https://github.com/ggerganov/ggml/blob/master/docs/gguf.md) files and load them
- Full [Ollama API](https://github.com/ollama/ollama/blob/main/docs/api.md) compatibility for seamless integration
- Web-based UI that's simple to customize and extend

**Current Limitations:**

- Text-only models supported at this time
- Embedding and image models not yet integrated
- No Android GPU acceleration (CPU inference only)
- Performance depends on device capabilities

## License

[MIT License](./LICENSE), same as [Ollama](https://github.com/ollama/ollama). This project builds upon [Ollama's](https://github.com/ollama/ollama) work to bring it to mobile platforms.
