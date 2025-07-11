package work.isdzulqor.oalla

import android.R.attr.path
import android.app.ComponentCaller
import android.content.Intent
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

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var logOutput: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var logToggleButton: Button
    private lateinit var mainPagerAdapter: MainPagerAdapter

    private val DEBUG_MODE = true // Set to false to hide log area and log button

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

        viewPager = findViewById(R.id.view_pager)
        bottomNav = findViewById(R.id.bottom_navigation)
        logOutput = findViewById(R.id.log_output)
        logScroll = findViewById(R.id.logScroll)
        logToggleButton = findViewById(R.id.log_toggle_button)

        // ViewPager adapter
        val adapter = MainPagerAdapter(this)
        viewPager.adapter = adapter
        mainPagerAdapter = adapter // <-- add this as a field
        viewPager.reduceSwipeSensitivity(factor = 2) // 2–5 is usually a good range

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
                }
            }
        } else {
            logToggleButton.visibility = View.GONE
            logScroll.visibility = View.GONE
        }

        // Ollama init
        copyAssetsToInternalStorage()
        startOllamaWithArgs()

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
    }

    private fun copyAssetsToInternalStorage() {
        val assetManager = assets
        val publicDir = File(filesDir, "public").apply {
            if (!exists()) mkdirs()
        }

        val assetFiles = assetManager.list("public") ?: return
        assetFiles.forEach { filename ->
            val outFile = File(publicDir, filename)
            assetManager.open("public/$filename").use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun startOllamaWithArgs() {
        Thread {
            try {
                val args = mutableListOf("serve", "--host", "localhost:$ollamaPort")
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

    fun logFromNative(msg: String) {
        runOnUiThread {
            logOutput.append("$msg\n")
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

    fun keepScreenOnFor(durationMs: Long) {
        runOnUiThread {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            runOnUiThread {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }, durationMs)
    }
}