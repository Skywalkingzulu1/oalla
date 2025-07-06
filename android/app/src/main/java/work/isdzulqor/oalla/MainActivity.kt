package work.isdzulqor.oalla

import android.app.ComponentCaller
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var logOutput: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var logToggleButton: Button
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
        viewPager.adapter = MainPagerAdapter(this)
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
                }
            }
        } else {
            logToggleButton.visibility = View.GONE
            logScroll.visibility = View.GONE
        }

        // Ollama init
        copyAssetsToInternalStorage()
        startOllamaWithArgs()
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
                val ollamaDir = File(filesDir, ".ollama")
                val blobsDir = File(ollamaDir, "blobs")
                val shaFile = blobsDir.listFiles()?.firstOrNull { it.name.startsWith("sha256-") }

                System.setProperty("OLLAMA_MODELS", ollamaDir.absolutePath)
                if (shaFile != null) {
                    Log.d("Ollama", "Using model: ${shaFile.absolutePath}")
                } else {
                    Log.w("Ollama", "No model found in blobs dir. Proceeding anyway.")
                }

                val args = arrayOf("serve", "--host", "localhost:$ollamaPort")
                runOllamaWithArgs(args)
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
}