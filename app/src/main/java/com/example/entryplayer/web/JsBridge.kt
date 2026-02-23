package com.example.entryplayer.web

import android.webkit.JavascriptInterface

/**
 * Bridge class exposing native methods to the WebView's JavaScript context.
 *
 * Currently used only to forward log messages from the WebView back to the Android UI.
 */
class JsBridge(private val onLog: (String) -> Unit) {
    @JavascriptInterface
    fun log(message: String) {
        onLog("[JS] $message")
    }
}