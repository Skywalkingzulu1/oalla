# Oalla Android

Running Go web server inside Android with JavaScript UI.

## Architecture

Oalla runs a complete Go web server inside the Android app process. The JavaScript UI communicates with this server via HTTP while also calling native Android functions through a bridge.

```
┌─────────────────────────────────────────────────────────────────┐
│                    Android App Process                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐    HTTP     ┌─────────────────────────┐    │
│  │   JavaScript    │ ←────────→  │     Go Server           │    │
│  │   Chat UI       │  localhost  │     (Ollama)            │    │
│  │   (WebView)     │ :8000-8500  │                         │    │
│  └─────────────────┘  (dynamic)  └─────────────────────────┘    │
│           │                                    │                │
│  ┌─────────────────┐             ┌─────────────────────────┐    │
│  │   Android       │   JSBridge  │    JNI Bridge           │    │
│  │   Bridge        │ ←────────→  │    (libbridgeollama.so) │    │
│  └─────────────────┘             └─────────────────────────┘    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Communication Flow:**
- **HTTP**: JavaScript ↔ Go server on dynamic localhost port
- **JSBridge**: JavaScript ↔ Android native functions  
- **JNI**: Android ↔ Go server lifecycle management

## Implementation

**Go Server Startup**

```kotlin
// Load native library
System.loadLibrary("bridgeollama")

// Start Go server
external fun runOllamaWithArgs(args: Array<String>)
runOllamaWithArgs(arrayOf("serve", "--host", "localhost:$SERVER_PORT"))
```

**JavaScript-Android Bridge**

```kotlin
inner class JSBridge {
    @JavascriptInterface
    fun hideBottomNav() {
        activity?.runOnUiThread { bottomNav.visibility = View.GONE }
    }
    
    @JavascriptInterface
    fun keepScreenOnFor(ms: Int) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.postDelayed({ 
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) 
        }, ms.toLong())
    }
}

webView.addJavascriptInterface(JSBridge(), "AndroidBridge")
```

**HTTP Communication**

```javascript
// JavaScript calls Go server
const response = await fetch(`http://localhost:${port}/api/chat`, {
    method: 'POST',
    headers: { 'User-Agent': 'secret-token' },
    body: JSON.stringify({ model, messages })
});

// JavaScript calls Android functions
AndroidBridge.hideBottomNav();
AndroidBridge.keepScreenOnFor(15000);
```

## Security Implementation

**Dynamic Port Allocation**

```kotlin
val SERVER_PORT: Int by lazy {
    val savedPort = prefs.getInt("server_port", -1)
    if (savedPort in 8000..8500 || savedPort == 9090) {
        savedPort  // Reuse existing port
    } else {
        // Choose random port (9090 for debug, random 8000-8500 for release)
        val chosenPort = if (DEBUG_MODE) 9090 else (8000..8500).random()
        prefs.edit().putInt("server_port", chosenPort).apply()
        chosenPort
    }
}
```

This prevents port conflicts and makes the server harder to discover from other apps.

**Authentication Token**

```kotlin
val USER_AGENT_SECRET = "ikilho-secrete-bosque-38298939"
webView.settings.userAgentString = USER_AGENT_SECRET
connection.setRequestProperty("User-Agent", USER_AGENT_SECRET)
```

**Localhost Binding**

```kotlin
runOllamaWithArgs(arrayOf(
    "serve",
    "--host", "localhost:$SERVER_PORT"  // Only localhost access
))
```

## Asset Management

**Encrypted Web UI**

```kotlin
private fun decryptAES(base64Input: String): ByteArray {
    val secretKey = "1234567890123456".toByteArray()
    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    return cipher.doFinal(Base64.decode(base64Input, Base64.DEFAULT))
}
```

Web UI files are encrypted in assets and decrypted at runtime to prevent tampering.

## Why This Works

This hybrid approach provides:

- Full Ollama API compatibility
- Rich JavaScript UI capabilities
- Native Android integration
- Offline-first operation
- Cross-platform UI potential

The Go server handles AI inference while JavaScript manages the complex chat interface. Android provides native system integration like notifications, file management, and lifecycle handling.

## Build Requirements

```kotlin
android {
    ndk {
        abiFilters += listOf("arm64-v8a")
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
```

Expected native libraries:

```
android/app/src/main/jniLibs/arm64-v8a/
├── libollama.so      # Go server
└── libbridgeollama.so # JNI bridge
```

## System Requirements

- Android 7.0+ (API 24)
- ARM64 device
- 4GB+ RAM for larger models

This architecture demonstrates embedding a complete web server in a mobile app while maintaining security and performance.
