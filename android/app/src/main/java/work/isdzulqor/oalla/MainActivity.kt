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

    private val ollamaPort = 8080
    external fun runOllamaWithArgs(args: Array<String>)

    companion object {
        init {
            try {
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

        startOllamaWithArgs()

        webView = binding.webview
        logOutput = binding.logOutput

        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.loadUrl("http://localhost:$ollamaPort/web")
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
                    "serve",
                    "--host", "127.0.0.1:8080",
                )
                runOllamaWithArgs(args)
            } catch (e: Exception) {
                Log.e("MainActivity", "Crash in startOllamaWithArgs: ${e.message}", e)
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