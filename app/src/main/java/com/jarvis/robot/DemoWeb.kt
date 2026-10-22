package com.jarvis.robot

import android.content.Context
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * The demo build's Advanced page: the control panel's own page, kept in the app's assets (assets/demo/panel/), served to the WebView under a made-up address
 * so it never touches the network. Its data comes from the json files in data/ through demo.js, which plays the panel's server (see tools/make_demo_data.py).
 */
object DemoWeb {
    const val HOST = "demo.jarvis"
    const val START = "https://$HOST/auto"

    /** The WebView's answer for one request to [HOST], or null for any other address (those are not opened in the demo). */
    fun serve(context: Context, request: WebResourceRequest): WebResourceResponse? {
        if (request.url.host != HOST) return null
        val path = request.url.encodedPath.orEmpty().trimStart('/').let { if (it.isEmpty() || it == "auto" || it == "login") "index.html" else it }
        val bytes = runCatching { context.assets.open("demo/panel/$path").use { it.readBytes() } }.getOrNull()
            ?: return page(404, "<html><body style='font:16px sans-serif;padding:24px'>This part of the backend is not in the demo.</body></html>".toByteArray(), "text/html")
        val ext = path.substringAfterLast('.', "")
        val type = when (ext) {
            "html" -> "text/html"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "webp" -> "image/webp"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
        return page(200, bytes, type)
    }

    private fun page(code: Int, bytes: ByteArray, type: String) =
        WebResourceResponse(type, "utf-8", code, if (code == 200) "OK" else "Not Found", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(bytes))
}
