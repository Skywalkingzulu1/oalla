package work.isdzulqor.oalla

import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import work.isdzulqor.oalla.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var logOutput: TextView
    private lateinit var binding: ActivityMainBinding

    private val ollamaPort = 9090
    external fun runOllamaWithArgs(args: Array<String>)

    companion object {
        init {
            try {
                System.loadLibrary("bridgeollama")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("JNI", "Failed to load native library: ${e.message}", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val webView = findViewById<WebView>(R.id.webview)
        val logOutput = findViewById<TextView>(R.id.log_output)
        val placeholderImage = findViewById<ImageView>(R.id.placeholder_image)
        val toggleLogButton = findViewById<Button>(R.id.toggleLogButton)
        val logScroll = findViewById<ScrollView>(R.id.logScroll)

        // Setup WebView
        webView.apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Hide the placeholder and show the WebView
                    placeholderImage.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                }
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            loadUrl("http://localhost:$ollamaPort/web")
        }

        // Setup log toggle button
        toggleLogButton.setOnClickListener {
            val layoutParams = logScroll.layoutParams as LinearLayout.LayoutParams

            if (logScroll.visibility == View.GONE || layoutParams.weight == 0f) {
                // Expand to 30% height
                layoutParams.weight = 0.3f
                logScroll.layoutParams = layoutParams
                logScroll.visibility = View.VISIBLE
                toggleLogButton.text = "Hide Logs"
            } else {
                // Collapse to 0 height
                layoutParams.weight = 0f
                logScroll.layoutParams = layoutParams
                logScroll.visibility = View.GONE
                toggleLogButton.text = "Show Logs"
            }
        }

        // Save for native access
        this.webView = webView
        this.logOutput = logOutput

        copyAssetsToInternalStorage()
        startOllamaWithArgs()
    }

    private fun setupWebView() {
        webView.apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            loadUrl("http://localhost:$ollamaPort/web")
        }
    }

    private fun copyAssetsToInternalStorage() {
        val assetManager = assets
        val publicDir = File(filesDir, "public").apply {
            if (!exists()) {
                val created = mkdirs()
                Log.d("AssetCopy", "Created public dir: $created at $absolutePath")
            } else {
                Log.d("AssetCopy", "Public dir already exists at $absolutePath")
            }
        }

        val assetFiles = assetManager.list("public") ?: run {
            Log.w("AssetCopy", "No files found under assets/public")
            return
        }

        assetFiles.forEach { filename ->
            val outFile = File(publicDir, filename)
            assetManager.open("public/$filename").use { inStream ->
                FileOutputStream(outFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
            Log.d("AssetCopy", "Copied: $filename to ${outFile.absolutePath}")
        }
    }

    private fun startOllamaWithArgs() {
        Thread {
            try {
                val ollamaDir = File(filesDir, ".ollama")
                val blobsDir = File(ollamaDir, "blobs")
                val shaFile = blobsDir.listFiles()?.firstOrNull { it.name.startsWith("sha256-") }

                System.setProperty("OLLAMA_MODELS", ollamaDir.absolutePath)
                Log.d("Ollama", "OLLAMA_MODELS = ${ollamaDir.absolutePath}")

                if (shaFile != null) {
                    Log.d("Ollama", "Using model: ${shaFile.absolutePath}")
                } else {
                    Log.w("Ollama", "No model found in blobs dir. Proceeding anyway.")
                }

                val args = arrayOf(
                    "serve",
                    "--host", "localhost:$ollamaPort"
                )

                runOllamaWithArgs(args)
            } catch (e: Exception) {
                Log.e("Ollama", "Failed to start Ollama: ${e.message}", e)
            }
        }.start()
    }

    // Called from native C++ to append logs to the UI
    fun logFromNative(msg: String) {
        runOnUiThread {
            logOutput.append("$msg\n")
        }
    }
}