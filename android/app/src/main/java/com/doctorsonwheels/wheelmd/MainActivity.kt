package com.doctorsonwheels.wheelmd

import android.R.attr.duration
import android.R.attr.path
import android.R.attr.text
import android.app.ActivityManager
import android.app.ComponentCaller
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import java.io.FileOutputStream
import androidx.activity.addCallback
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.nio.file.Files.exists
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import android.util.Log.e
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.edit
import kotlin.jvm.java

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var logOutput: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var logToggleButton: Button
    private lateinit var mainPagerAdapter: MainPagerAdapter

    val DEBUG_MODE = false // Set to false to hide log area and log button
    val USER_AGENT_SECRET = "ikilho-secrete-bosque-38298939"

    val SERVER_PORT: Int = 11434
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

        viewPager = findViewById(R.id.view_pager)
        bottomNav = findViewById(R.id.bottom_navigation)
        logOutput = findViewById(R.id.log_output)
        logScroll = findViewById(R.id.logScroll)
        logToggleButton = findViewById(R.id.log_toggle_button)

        // ViewPager adapter
        val adapter = MainPagerAdapter(this)
        viewPager.adapter = adapter
        mainPagerAdapter = adapter // <-- add this as a field
        viewPager.reduceSwipeSensitivity(factor = 3) // 2–5 is usually a good range

        // BottomNav → ViewPager
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chat -> viewPager.currentItem = 0
                R.id.nav_model -> viewPager.currentItem = 1
                R.id.nav_about -> viewPager.currentItem = 2
            }
            true
        }

        // ViewPager → BottomNav
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.menu.getItem(position).isChecked = true
            }
        })

        // Default tab
        bottomNav.selectedItemId = R.id.nav_chat

        if (DEBUG_MODE) {
            logToggleButton.visibility = View.VISIBLE
            logScroll.visibility = View.GONE // Start collapsed
            logToggleButton.setOnClickListener {
                if (logScroll.visibility == View.VISIBLE) {
                    logScroll.visibility = View.GONE
                    logToggleButton.text = "LOG"
                } else {
                    logScroll.visibility = View.VISIBLE
                    logToggleButton.text = "HIDE"

                    // Scroll to bottom after layout pass
                    logScroll.post {
                        logScroll.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        } else {
            logToggleButton.visibility = View.GONE
            logScroll.visibility = View.GONE
        }

        // Ollama init
        copyAssetsToInternalStorage()
        startOllamaWithArgs()
        
        // Wait for server to be ready before loading WebView
        waitForServerAndLoadWebView()

        onBackPressedDispatcher.addCallback(this@MainActivity) {
            val currentFragment = mainPagerAdapter.getFragment(viewPager.currentItem)
            if (currentFragment is ChatFragment) {
                val webView = currentFragment.webView
                if (webView != null) {
                    webView.evaluateJavascript("AndroidBridge.onBackPressed()", null)
                    return@addCallback
                }
            }

            // fallback
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }

        if (DEBUG_MODE) {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val defaultHeap = activityManager.memoryClass // without largeHeap
            val largeHeap = activityManager.largeMemoryClass // with largeHeap

            val isLargeHeap = applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP != 0
            val text = if (isLargeHeap) {
                "App large heap limit: ${largeHeap}MB"
            } else {
                "App default heap limit: ${defaultHeap}MB"
            }

            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }

        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?, durationMs: Long = 2000) {
        intent?.let {
            if (it.getBooleanExtra("navigate_to_chat", false)) {
                it.removeExtra("navigate_to_chat") // prevent re-trigger
                val chatId = it.getStringExtra("chat_id") ?: return@let
                val chatIndex = it.getIntExtra("chat_index", 0)
                viewPager.currentItem = 0
                Log.e("GoToChat", "navigate to chat id: $chatId")

                Handler(Looper.getMainLooper()).postDelayed({
                    notifyWebViewAppResumed()
                    val chatFragment = mainPagerAdapter.getFragment(0) as? ChatFragment
                    chatFragment?.webView?.evaluateJavascript(
                        "window.goToChat && window.goToChat('$chatId', $chatIndex);",
                        null
                    )

                    AssistantNotificationService.stopNotification(this)
                }, durationMs)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // update internal reference to new intent
        handleNotificationIntent(intent, 1000)
    }

    private fun copyAssetsToInternalStorage() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val storedVersion = prefs.getLong("copiedAssetsVersion", -1)

        val assetManager = assets
        val versionStream = try {
            assetManager.open("public/assets_version.txt")
        } catch (e: Exception) {
            Log.e("AssetCopy", "Version file missing or unreadable", e)
            null
        }

        val currentVersion = versionStream?.bufferedReader()?.use { it.readLine().toLongOrNull() } ?: -1L

        if (storedVersion >= currentVersion && currentVersion > 0) return

        val publicDir = File(filesDir, "public")

        if (publicDir.exists()) {
            publicDir.deleteRecursively()
        }
        publicDir.mkdirs()

        val assetFiles = assetManager.list("public")?.filterNot { it == "assets_version.txt" } ?: return

        assetFiles.forEach { filename ->
            try {
                val outFile = File(publicDir, filename)
                assetManager.open("public/$filename").use { input ->
                    val outputStream = FileOutputStream(outFile)
                    val encryptedBase64 = input.readBytes().toString(Charsets.UTF_8)
                    val decryptedBytes = decryptAES(encryptedBase64)
                    outputStream.write(decryptedBytes)
                    outputStream.close()
                }
            } catch (e: Exception) {
                Log.e("AssetCopy", "Failed to copy or decrypt $filename", e)
            }
        }

        Log.d("AssetCopy", "Copied files list:")
        publicDir.walkTopDown().forEach {
            Log.d("AssetCopy", " - ${it.absolutePath}")
        }

        prefs.edit().putLong("copiedAssetsVersion", currentVersion).apply()
    }

    private fun decryptAES(base64Input: String): ByteArray {
        return try {
            val secretKey = "1234567890123456".toByteArray()
            val decodedBytes = Base64.decode(base64Input, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec(secretKey, "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            cipher.doFinal(decodedBytes)
        } catch (e: Exception) {
            Log.e("Decrypt", "Decryption failed: ${e::class.java.simpleName} - ${e.message}", e)
            throw e
        }
    }

    private fun waitForServerAndLoadWebView() {
        Thread {
            var attempts = 0
            val maxAttempts = 30 // 30 seconds timeout
            
            while (attempts < maxAttempts) {
                try {
                    val url = "http://localhost:$SERVER_PORT/"
                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 1000
                    connection.readTimeout = 1000
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", USER_AGENT_SECRET)

                    val responseCode = connection.responseCode
                    connection.disconnect()
                    
                    if (responseCode == 200) {
                        Log.d("Ollama", "Server is ready on port $SERVER_PORT")
                        runOnUiThread {
                            loadWebViewContent()
                        }
                        autoPullWheelmd()
                        return@Thread
                    }
                } catch (e: Exception) {
                    Log.d("Ollama", "Server not ready yet, attempt ${attempts + 1}/$maxAttempts")
                }
                
                attempts++
                Thread.sleep(1000) // Wait 1 second between attempts
            }
            
            Log.e("Ollama", "Server failed to start after $maxAttempts seconds")
            runOnUiThread {
                // Show error message or fallback UI
                Log.e("WebView", "Failed to start Ollama server, loading anyway...")
                loadWebViewContent()
            }
        }.start()
    }

    private fun loadWebViewContent() {
        // Get the ChatFragment from the adapter and tell it to load the WebView
        val chatFragment = mainPagerAdapter.getFragment(0) as? ChatFragment
        
        chatFragment?.let { fragment ->
            Log.d("WebView", "Telling ChatFragment to load WebView")
            fragment.loadWebViewWhenReady()
        } ?: run {
            Log.w("WebView", "ChatFragment not found in adapter")
        }
    }

    private fun startOllamaWithArgs() {
        Thread {
            try {
                val url = "http://localhost:$SERVER_PORT/"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 1000
                connection.readTimeout = 1000
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", USER_AGENT_SECRET)

                val isRunning = try {
                    val code = connection.responseCode
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    code == 200 && body.lowercase().contains("ollama")
                } catch (e: Exception) {
                    false
                } finally {
                    connection.disconnect()
                }

                if (isRunning) {
                    Log.d("Ollama", "Ollama already running — skipping startup")
                    autoPullWheelmd()
                    return@Thread
                }

                val args = mutableListOf(
                    "serve",
                    "--debug", "$DEBUG_MODE",
                    "--host", "localhost:$SERVER_PORT",
                    "--useragent-secret", USER_AGENT_SECRET,
                )

                val storagePref = getSharedPreferences("model_prefs", 0).getString("storage_model", "internal")
                val modelPath = if (storagePref == "external") {
                    getExternalFilesDir("ollama_models")?.absolutePath
                } else {
                    File(filesDir, ".ollama").absolutePath
                }

                Log.d("Ollama", "Using model path: $modelPath")
                args.add("--models")
                args.add(modelPath.toString())

                runOllamaWithArgs(args.toTypedArray())

            } catch (e: Exception) {
                Log.e("Ollama", "Failed to start Ollama: ${e.message}", e)
            }
        }.start()
    }

    private fun autoPullWheelmd() {
        Thread {
            try {
                // Check if wheelmd already exists
                val tagsUrl = "http://localhost:$SERVER_PORT/api/tags"
                val tagsConnection = java.net.URL(tagsUrl).openConnection() as java.net.HttpURLConnection
                tagsConnection.connectTimeout = 5000
                tagsConnection.readTimeout = 5000
                tagsConnection.requestMethod = "GET"
                tagsConnection.setRequestProperty("User-Agent", USER_AGENT_SECRET)

                val tagsBody = tagsConnection.inputStream.bufferedReader().use { it.readText() }
                tagsConnection.disconnect()

                if (tagsBody.contains("wheelmd")) {
                    Log.d("Ollama", "wheelmd model already exists, skipping pull")
                    return@Thread
                }

                Log.d("Ollama", "wheelmd not found, starting auto-pull...")
                runOnUiThread {
                    Toast.makeText(this, "Downloading WheelMD AI model (~1.6GB)...", Toast.LENGTH_LONG).show()
                }

                // Pull wheelmd model
                val pullUrl = "http://localhost:$SERVER_PORT/api/pull"
                val pullConnection = java.net.URL(pullUrl).openConnection() as java.net.HttpURLConnection
                pullConnection.requestMethod = "POST"
                pullConnection.setRequestProperty("Content-Type", "application/json")
                pullConnection.setRequestProperty("User-Agent", USER_AGENT_SECRET)
                pullConnection.doOutput = true
                pullConnection.outputStream.bufferedWriter().use { it.write("""{"name":"wheelmd"}""") }

                // Read response to ensure pull completes
                pullConnection.inputStream.bufferedReader().use { it.readText() }
                pullConnection.disconnect()

                Log.d("Ollama", "wheelmd model pull complete")
                runOnUiThread {
                    Toast.makeText(this, "WheelMD AI model ready!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Ollama", "Auto-pull wheelmd failed: ${e.message}", e)
            }
        }.start()
    }

    fun logFromNative(msg: String) {
        runOnUiThread {
            logOutput.append("$msg")
        }
    }

    fun hideSplash() {
        runOnUiThread {
            findViewById<View>(R.id.splash_overlay).visibility = View.GONE
            bottomNav.visibility = View.VISIBLE
            logToggleButton.visibility = if (DEBUG_MODE) View.VISIBLE else View.GONE
        }
    }

    fun hideBottomNav() {
        runOnUiThread {
            bottomNav.isEnabled = false
            bottomNav.isClickable = false
            bottomNav.isFocusable = false

            viewPager.isUserInputEnabled = false

            bottomNav.animate()
                .alpha(0f)
                .translationY(bottomNav.height.toFloat())
                .setDuration(200)
                .withEndAction {
                    bottomNav.visibility = View.GONE
                }
                .start()
        }
    }

    fun showBottomNav() {
        runOnUiThread {
            bottomNav.visibility = View.VISIBLE
            bottomNav.alpha = 0f
            bottomNav.translationY = bottomNav.height.toFloat()

            bottomNav.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .withStartAction {
                    // Restore interaction after it becomes visible
                    bottomNav.isEnabled = true
                    bottomNav.isClickable = true
                    bottomNav.isFocusable = true
                    viewPager.isUserInputEnabled = true // Re-enable swipe
                }
                .start()
        }
    }

    private var clearKeepScreenOnRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    fun keepScreenOnFor(durationMs: Long) {
        runOnUiThread {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // Cancel the previous runnable if it exists
        clearKeepScreenOnRunnable?.let { handler.removeCallbacks(it) }

        // Create a new one
        clearKeepScreenOnRunnable = Runnable {
            runOnUiThread {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        // Post the new one
        handler.postDelayed(clearKeepScreenOnRunnable!!, durationMs)
    }

    fun setTab(index: Int) {
        runOnUiThread {
            viewPager.currentItem = index
        }
    }

    override fun onPause() {
        super.onPause()
        notifyWebViewAppMinimized()
    }

    override fun onResume() {
        super.onResume()
        notifyWebViewAppResumed()
    }

    private fun notifyWebViewAppMinimized() {
        val currentFragment = mainPagerAdapter.getFragment(viewPager.currentItem)
        if (currentFragment is ChatFragment) {
            currentFragment.webView?.evaluateJavascript(
                "window.onAppMinimized && window.onAppMinimized();", null
            )
        }
    }

    private fun notifyWebViewAppResumed() {
        val currentFragment = mainPagerAdapter.getFragment(viewPager.currentItem)
        if (currentFragment is ChatFragment) {
            currentFragment.webView?.evaluateJavascript(
                "window.onAppResumed && window.onAppResumed();", null
            )
        }
    }
}
