package com.example.appkalacam.ws

import android.util.Log
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class FaceWebSocketListener(
    private val validator: FaceValidator
) : WebSocketListener() {

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d("WS_FACE", "WebSocket conectado")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        validator.handleMessage(text)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e("WS_FACE", "Error WebSocket: ${t.message}")
    }
}
