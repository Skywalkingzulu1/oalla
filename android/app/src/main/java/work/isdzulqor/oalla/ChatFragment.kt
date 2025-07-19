package work.isdzulqor.oalla

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
import android.webkit.WebViewFragment
import androidx.fragment.app.Fragment

class ChatFragment : Fragment() {

    private val ollamaPort = 9090
    private var splashStartTime: Long = 0

    // Expose webView so MainActivity can access it for back press
    var webView: WebView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        splashStartTime = System.currentTimeMillis()

        val root = inflater.inflate(R.layout.fragment_chat, container, false)
        webView = root.findViewById(R.id.webview)

        webView?.apply {
            visibility = View.GONE

            webViewClient = object : WebViewClient() {
                var loadError = false

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()

                    // Whitelist: allow anything from your internal Ollama server
                    if (url.startsWith("http://localhost:$ollamaPort")) {
                        return false // Load inside WebView
                    }

                    // Everything else: open in external browser
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    view?.context?.startActivity(intent)
                    return true
                }

                // For older API levels (< 24)
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url != null && url.startsWith("http://localhost:$ollamaPort")) {
                        return false
                    }

                    if (url != null && url.startsWith("http")) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        view?.context?.startActivity(intent)
                        return true
                    }

                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!loadError) {
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

            settings.javaScriptEnabled = true
            addJavascriptInterface(JSBridge(),"AndroidBridge")
            settings.domStorageEnabled = true
            loadUrl("http://localhost:$ollamaPort/web")
        }
        return root
    }

    override fun onResume() {
        super.onResume()

        // Trigger JS-side logic when the fragment becomes active again
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
    }
}