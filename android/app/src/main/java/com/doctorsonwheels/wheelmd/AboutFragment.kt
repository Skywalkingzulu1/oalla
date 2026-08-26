package com.doctorsonwheels.wheelmd

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [AboutFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AboutFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment AboutFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            AboutFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val textView = view.findViewById<TextView>(R.id.about_content)

        val fullText = """
        OALLA is our small experiment to make AI run directly on your device — kept offline, kept private.

        Thanks to open-source projects like llama.cpp, Ollama, and many others contributing amazing models, this is finally possible.

        Is it fast? On high-spec devices, sure. On most phones... it's getting there.
        But it works — and it's private, local, and evolving f̶a̶s̶t̶.

        We're building:
        – MCP Integration
        – Custom AI Agent
        – Other stuff we're still testing at 2AM

        Have feedback or ideas?
        Drop us a line: oalla@isdzulqor.work

        — The OALLA Team
    """.trimIndent()

        val spannable = SpannableString(fullText)

        val links = mapOf(
            "llama.cpp" to "https://github.com/ggml-org/llama.cpp",
            "Ollama" to "https://github.com/ollama/ollama",
            "oalla@isdzulqor.work" to "mailto:oalla@isdzulqor.work"
        )

        links.forEach { (text, url) ->
            val index = fullText.indexOf(text)
            if (index >= 0) {
                spannable.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.color = ContextCompat.getColor(requireContext(), R.color.teal_200)
                        ds.isUnderlineText = false
                    }
                }, index, index + text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        textView.text = spannable
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = Color.TRANSPARENT
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }
}
