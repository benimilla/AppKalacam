package com.example.appkalacam.ws

import android.util.Base64
import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class ValidarRostroWS(
    url: String,
    private val onMensaje: (RespuestaWS) -> Unit,
    private val onEstado: (String) -> Unit
) : WebSocketClient(URI(url)) {

    override fun onOpen(handshakedata: ServerHandshake?) {
        onEstado("🔌 Conectado al servidor")
    }

    override fun onMessage(message: String?) {
        if (message == null) return

        try {
            val respuesta = RespuestaWS.desdeJson(message)
            onMensaje(respuesta)

        } catch (e: Exception) {
            Log.e("WS", "Error procesando mensaje WS: ${e.message}")
        }
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        onEstado("❌ Conexión cerrada: $reason")
    }

    override fun onError(ex: Exception?) {
        onEstado("⚠ Error en WebSocket: ${ex?.message}")
    }

    /** 🔥 Enviar imagen al backend en Base64 */
    fun enviarImagenBytes(bytes: ByteArray) {
        try {
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val json = """
                {
                    "tipo": "imagen",
                    "content_type": "image/jpeg",
                    "imagen": "$base64"
                }
            """.trimIndent()

            send(json)

        } catch (e: Exception) {
            Log.e("WS", "Error al enviar imagen: ${e.message}")
        }
    }
}
