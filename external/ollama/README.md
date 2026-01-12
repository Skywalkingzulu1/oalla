# Converting Ollama for Android

How to modify the official Ollama repository to run on Android devices.

## Process Overview

Three main modifications are needed:

1. Add Android-specific code (JNI bridge and entry point)
2. Modify core files for in-process execution
3. Create cross-compilation build system

## Step-by-Step Conversion

### 1. Clone Official Ollama

```bash
git clone https://github.com/ollama/ollama.git
cd ollama
```

### 2. Add Android Files

Create these files in the ollama directory:

**android-main.go** - Entry point for Android

```go
package main

import "C"
import (
    "context"
    "os"
    "unsafe"
    "github.com/ollama/ollama/cmd"
    "github.com/ollama/ollama/llm"
    "github.com/ollama/ollama/runner"
)

func init() {
    llm.RunRunnerFunc = func(args []string) error {
        return runner.Execute(args)
    }
}

//export runOllamaWithArgs
func runOllamaWithArgs(argv **C.char, argc C.int) {
    args := make([]string, int(argc))
    argPtrs := (*[1 << 16]*C.char)(unsafe.Pointer(argv))[:argc:argc]
    for i := range args {
        args[i] = C.GoString(argPtrs[i])
    }

    os.Setenv("OLLAMA_MODELS", "/data/data/your.app.package/files/.ollama")
    os.Setenv("OLLAMA_WEB_STATIC_DIR", "/data/data/your.app.package/files/public")
    os.Setenv("HOME", "/data/data/your.app.package/files")

    cli := cmd.NewCLI()
    cli.SetArgs(args)
    cli.ExecuteContext(context.Background())
}

func main() {}
```

**jni_bridgeollama.cpp** - JNI bridge

```cpp
#include <jni.h>

extern "C" JNIEXPORT void JNICALL
Java_your_package_MainActivity_runOllamaWithArgs(JNIEnv* env, jobject thiz, jobjectArray args) {
    int argc = env->GetArrayLength(args);
    char** argv = new char*[argc];

    for (int i = 0; i < argc; i++) {
        jstring jstr = (jstring) env->GetObjectArrayElement(args, i);
        const char* cstr = env->GetStringUTFChars(jstr, 0);
        argv[i] = strdup(cstr);
        env->ReleaseStringUTFChars(jstr, cstr);
        env->DeleteLocalRef(jstr);
    }

    extern void runOllamaWithArgs(char** argv, int argc);
    runOllamaWithArgs(argv, argc);

    for (int i = 0; i < argc; i++) free(argv[i]);
    delete[] argv;
}
```

**build_android.sh** - Build script

```bash
#!/bin/bash
export NDK_HOME=~/Library/Android/sdk/ndk/26.1.10909125
export TOOLCHAIN=$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64
export CC=$TOOLCHAIN/bin/aarch64-linux-android21-clang
export CXX="$PWD/android-clang-wrapper.sh"
export CGO_LDFLAGS="-ldl -llog -lm -lstdc++"

CGO_ENABLED=1 GOOS=android GOARCH=arm64 \
  go build -buildmode=c-shared -o libollama.so android-main.go
```

**android-clang-wrapper.sh** - C++ wrapper

```bash
#!/bin/bash
ARGS=()
for arg in "$@"; do
  if [[ "$arg" != "-lrt" && "$arg" != "-lpthread" ]]; then
    ARGS+=("$arg")
  fi
done
exec $NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android21-clang++ "${ARGS[@]}"
```

### 3. Modify Core Files

**llm/server.go** - Add in-process support

```go
// Add at top of file
var RunRunnerFunc func(args []string) error

// In NewLlamaServer function
useInProcessRunner := RunRunnerFunc != nil
s := &llmServer{
    port:      port,
    inProcess: useInProcessRunner,
}

if useInProcessRunner {
    go func() {
        err := RunRunnerFunc(finalParams[1:])
        s.done <- err
    }()
}
```

**llama/llama.cpp/src/llama-mmap.cpp** - Android memory fix

```cpp
#ifdef __ANDROID__
#include <unistd.h>
#include <sys/mman.h>
#define POSIX_MADV_WILLNEED 3
#define POSIX_MADV_RANDOM 1
#define posix_madvise(addr, len, advice) madvise(addr, len, advice)
#endif
```

### 4. Build

```bash
chmod +x build_android.sh android-clang-wrapper.sh
./build_android.sh
```

Output: `libollama.so` and `libollama.h`

## Web Interface and API

The converted Ollama provides:

**Web UI**

- Chat interface: `http://localhost:PORT/web`
- Static files served from `OLLAMA_WEB_STATIC_DIR`

**REST API**

- `POST /api/chat` - Stream chat completions
- `GET /api/tags` - List models
- `POST /api/pull` - Download models
- `DELETE /api/delete` - Remove models
- `POST /api/generate` - Single completions

**Android Usage**

```kotlin
// Load web interface
webView.loadUrl("http://localhost:$SERVER_PORT/web")

// API calls from JavaScript
fetch('http://localhost:8080/api/chat', {
    method: 'POST',
    headers: { 'User-Agent': 'secret-token' },
    body: JSON.stringify({ model: 'llama3.1', messages: [...] })
})
```

## Key Changes

**In-Process Execution**: `RunRunnerFunc` callback eliminates subprocess overhead
**Android Memory**: Fixes `madvise` compatibility for model loading
**JNI Bridge**: Enables direct Java-to-Go communication
**Cross-Compilation**: NDK toolchain builds ARM64 native library

## Requirements

- Android NDK 26.1.10909125
- Go 1.21+ with CGO
- ARM64 target devices (API 21+)

This process transforms standard Ollama into an Android-compatible library while maintaining full API compatibility.
