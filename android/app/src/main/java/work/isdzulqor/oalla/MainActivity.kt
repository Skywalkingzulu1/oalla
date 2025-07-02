package work.isdzulqor.oalla

import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import work.isdzulqor.oalla.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var logOutput: TextView
    private lateinit var binding: ActivityMainBinding

    private val httpPort = 9090
    private val ollamaPort = 8080
    private val ollamaRunnerPort = 42037

    external fun startServer()
    external fun runOllama()
    external fun runOllamaRunner(args: Array<String>)
    external fun runOllamaWithArgs(args: Array<String>)


    companion object {
        init {
            try {
                // load JNI bridge of http
                System.loadLibrary("bridge")

                // load JNI bridge of ollama
                System.loadLibrary("bridgeollama")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("JNI", "Library load failed: ${e.message}")
            }
        }
    }

    private fun copyAssetsToInternalStorage() {
        val assetManager = assets
        val publicDir = File(filesDir, "public")
        if (!publicDir.exists()) {
            val created = publicDir.mkdirs()
            Log.d("AssetCopy", "Created public dir: $created at ${publicDir.absolutePath}")
        } else {
            Log.d("AssetCopy", "Public dir already exists at ${publicDir.absolutePath}")
        }

        val files = assetManager.list("public") ?: run {
            Log.w("AssetCopy", "No files found under assets/public")
            return
        }

        for (filename in files) {
            val inStream = assetManager.open("public/$filename")
            val outFile = File(publicDir, filename)
            val outStream = FileOutputStream(outFile)

            inStream.copyTo(outStream)
            inStream.close()
            outStream.close()

            Log.d("AssetCopy", "Copied file: $filename to ${outFile.absolutePath}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        copyAssetsToInternalStorage()

//        startHTTPServer()
        startOllamaServer()
//        startOllamaRunner()
//        startOllamaWithArgs()



        webView = binding.webview
        logOutput = binding.logOutput

        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
//        webView.loadUrl("http://localhost:$httpPort")

        webView.loadUrl("http://localhost:$ollamaPort/web")
//        webView.loadUrl("http://localhost:${ollamaRunnerPort}")
    }

    fun startHTTPServer() {
        // Start the Go HTTP server in a background thread
        Thread {
            startServer()
        }.start()
    }

    fun startOllamaServer() {
        // Start the Go Ollama server in a background thread
        Thread {
            try {
                val ollamaDir = File(filesDir, ".ollama").apply { mkdirs() }
                val staticDir = File(filesDir, "public").apply { mkdirs() }

                System.setProperty("OLLAMA_MODELS", ollamaDir.absolutePath)
                System.setProperty("OLLAMA_WEB_STATIC_DIR", staticDir.absolutePath)

                Log.d("MainActivity", "OLLAMA_MODELS = ${ollamaDir.absolutePath}")

                Log.d("MainActivity", "Calling runOllama")
                runOllama()
                Log.d("MainActivity", "Returned from runOllama")
            } catch (e: Exception) {
                Log.e("MainActivity", "Crash in runOllama: ${e.message}", e)
            }
        }.start()
    }

    fun startOllamaRunner() {
        // Start the Go Ollama runner in a background thread
        Thread {
            try {
                val ollamaDir = File(filesDir, ".ollama")
                val blobsDir = File(ollamaDir, "blobs")
                val shaFile = blobsDir.listFiles()?.firstOrNull { it.name.startsWith("sha256-") }

                if (shaFile == null) {
                    Log.e("MainActivity", "No model file found in blobs directory.")
                    return@Thread
                }

                val modelPath = shaFile.absolutePath
                System.setProperty("OLLAMA_MODELS", ollamaDir.absolutePath)
                Log.d("MainActivity", "OLLAMA_MODELS = ${ollamaDir.absolutePath}")
                Log.d("MainActivity", "Using model = $modelPath")

                val args = arrayOf(
                    "--model", modelPath,
                    "--ctx-size", "8192",
                    "--batch-size", "512",
                    "--threads", "4",
                    "--no-mmap",
                    "--parallel", "2",
                    "--port", "42037"
                )
                runOllamaRunner(args)
            } catch (e: Exception) {
                Log.e("MainActivity", "Crash in startOllamaRunner: ${e.message}", e)
            }
        }.start()
    }

    fun startOllamaWithArgs() {
        // Start the Go Ollama runner in a background thread
        Thread {
            try {
                val ollamaDir = File(filesDir, ".ollama")
                val blobsDir = File(ollamaDir, "blobs")
                val shaFile = blobsDir.listFiles()?.firstOrNull { it.name.startsWith("sha256-") }

                if (shaFile == null) {
                    Log.e("MainActivity", "No model file found in blobs directory.")
                    return@Thread
                }

                val modelPath = shaFile.absolutePath
                System.setProperty("OLLAMA_MODELS", ollamaDir.absolutePath)
                Log.d("MainActivity", "OLLAMA_MODELS = ${ollamaDir.absolutePath}")
                Log.d("MainActivity", "Using model = $modelPath")

                val args = arrayOf(
                    "runner",
                    "--model", modelPath,
                    "--ctx-size", "8192",
                    "--batch-size", "512",
                    "--threads", "4",
                    "--no-mmap",
                    "--parallel", "2",
                    "--port", "8080"
                )
                runOllamaRunner(args)
            } catch (e: Exception) {
                Log.e("MainActivity", "Crash in startOllamaRunner: ${e.message}", e)
            }
        }.start()
    }

    // Called from native C++ code to append logs to the UI
    fun logFromNative(msg: String) {
        runOnUiThread {
            logOutput.append("$msg\n")
        }
    }

}