package com.example.appkalacam.ws

import android.util.Log
import okhttp3.*
import okio.ByteString

class WebSocketManager(
    private val url: String,
    private val listener: WebSocketListener
) {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    fun connect() {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, listener)
    }

    fun send(message: String) {
        webSocket?.send(message)
    }

    fun close() {
        webSocket?.close(1000, "Closing from client")
    }
}
