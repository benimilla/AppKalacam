package com.example.appkalacam.ws

import android.util.Log

class FaceValidator(
    private val onResult: (Boolean, String) -> Unit
) {

    fun handleMessage(msg: String) {
        Log.d("WS_FACE", "Mensaje recibido: $msg")

        when {
            msg.contains("rostro_detectado", ignoreCase = true) ->
                onResult(true, "Rostro detectado correctamente")

            msg.contains("sin_rostro", ignoreCase = true) ->
                onResult(false, "No se detectó rostro")

            else ->
                onResult(false, "Respuesta desconocida: $msg")
        }
    }
}
