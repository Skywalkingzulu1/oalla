package com.doctorsonwheels.wheelmd

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment

class ChatFragment : Fragment() {

    private var splashStartTime: Long = 0
    private var isPageLoaded: Boolean = false

    private val debugMode: Boolean
        get() = (activity as? MainActivity)?.DEBUG_MODE ?: false

    var webView: WebView? = null

    private val ollamaPort: Int
        get() = (activity as? MainActivity)?.SERVER_PORT
            ?: throw IllegalStateException("SERVER_PORT is not set in MainActivity")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        splashStartTime = System.currentTimeMillis()

        val root = inflater.inflate(R.layout.fragment_chat, container, false)
        webView = root.findViewById(R.id.webview)
        val userAgentSecret = (activity as? MainActivity)?.USER_AGENT_SECRET

        webView?.apply {
            visibility = View.GONE

            webViewClient = object : WebViewClient() {
                var loadError = false

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (url.startsWith("http://localhost:$ollamaPort")) return false
                    if (url.startsWith("https://skywalkingzulu1.github.io")) return false
                    if (url.startsWith("https://jvsfhrekkkhijneqngax.supabase.co")) return false
                    view?.context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url != null && url.startsWith("http://localhost:$ollamaPort")) return false
                    if (url != null && url.startsWith("https://skywalkingzulu1.github.io")) return false
                    if (url != null && url.startsWith("https://jvsfhrekkkhijneqngax.supabase.co")) return false
                    if (url != null && url.startsWith("http")) {
                        view?.context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!loadError) {
                        isPageLoaded = true
                        val elapsed = System.currentTimeMillis() - splashStartTime
                        val remaining = 1500L - elapsed

                        postDelayed({
                            (activity as? MainActivity)?.hideSplash()
                            visibility = View.VISIBLE
                        }, remaining.coerceAtLeast(0))
                    } else {
                        Log.w("WebView", "Page failed to load, keeping splash visible.")
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    loadError = true
                    Log.e("WebView", "onReceivedError: $description [$errorCode] for $failingUrl")
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    loadError = true
                    Log.e("WebView", "onReceivedHttpError: ${errorResponse?.statusCode} ${errorResponse?.reasonPhrase}")
                }
            }

            Log.e("WebView", "userAgentSecret: $userAgentSecret")
            settings.userAgentString = userAgentSecret
            settings.javaScriptEnabled = true
            addJavascriptInterface(JSBridge(), "AndroidBridge")
            settings.domStorageEnabled = true
            
            // Don't load URL immediately, wait for server to be ready
            Log.d("WebView", "WebView configured, waiting for server...")
        }

        return root
    }
    
    fun loadWebViewWhenReady() {
        // Make sure the view is created and WebView is available
        if (webView == null) {
            Log.w("WebView", "WebView not ready yet, will retry in 500ms")
            view?.postDelayed({ loadWebViewWhenReady() }, 500)
            return
        }
        
        webView?.post {
            Log.d("WebView", "Loading WebView URL: https://skywalkingzulu1.github.io/leviathan/")
            webView?.loadUrl("https://skywalkingzulu1.github.io/leviathan/")
        }
    }

    override fun onResume() {
        super.onResume()

        if (isPageLoaded) {
            (activity as? MainActivity)?.hideSplash()
            webView?.visibility = View.VISIBLE
        }

        webView?.evaluateJavascript("window.refreshModelList && window.refreshModelList();", null)
    }

    inner class JSBridge {
        @android.webkit.JavascriptInterface
        fun onBackPressed() {
            activity?.runOnUiThread {
                webView?.evaluateJavascript("window.onNativeBackPressed && window.onNativeBackPressed()", null)
            }
        }

        @android.webkit.JavascriptInterface
        fun hideBottomNav() {
            (activity as? MainActivity)?.hideBottomNav()
        }

        @android.webkit.JavascriptInterface
        fun showBottomNav() {
            (activity as? MainActivity)?.showBottomNav()
        }

        @android.webkit.JavascriptInterface
        fun keepScreenOnFor(ms: Int) {
            (activity as? MainActivity)?.keepScreenOnFor(ms.toLong())
        }

        @android.webkit.JavascriptInterface
        fun navigateTo(tab: String) {
            (activity as? MainActivity)?.let {
                when (tab.lowercase()) {
                    "chat" -> it.setTab(0)
                    "model" -> it.setTab(1)
                    "about" -> it.setTab(2)
                    else -> Log.w("JSBridge", "Unknown tab: $tab")
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun showToast(text: String, isLongDur: Boolean = false, shouldDebug: Boolean = false) {
            if (shouldDebug && !debugMode) return
            val duration = if (isLongDur) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            Toast.makeText(requireContext(), text, duration).show()
        }

        @android.webkit.JavascriptInterface
        fun updateAssistantNotification(text: String, chatId: String, messageIndex: Int, isComplete: Boolean = false) {
            val maxLength = 120
            val trimmedText = if (text.length > maxLength) {
                text.take(maxLength).trimEnd() + "… more"
            } else {
                text
            }

            val isAppActive = activity?.lifecycle?.currentState
                ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true

            if (!isAppActive) {
                // only push notif when user is not using the app
                AssistantNotificationService.updateNotification(
                    requireContext(),
                    trimmedText,
                    chatId,
                    messageIndex,
                    isComplete
                )
            } else {
                Log.d("AssistantNotif", "Skipped notification: app is active")
            }
        }
    }
}
