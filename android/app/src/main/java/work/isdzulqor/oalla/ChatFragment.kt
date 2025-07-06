package work.isdzulqor.oalla

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment

class ChatFragment : Fragment() {

    private val ollamaPort = 9090
    private var splashStartTime: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        splashStartTime = System.currentTimeMillis()

        val root = inflater.inflate(R.layout.fragment_chat, container, false)
        val webView = root.findViewById<WebView>(R.id.webview)

        webView.apply {
            visibility = View.GONE

            webViewClient = object : WebViewClient() {
                var loadError = false

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
            settings.domStorageEnabled = true
            loadUrl("http://localhost:$ollamaPort/web")
        }

        return root
    }
}