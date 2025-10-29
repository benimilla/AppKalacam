package com.example.appkalacam.ws

import org.json.JSONObject

data class RespuestaWS(
    val ok: Boolean? = null,
    val rostroDetectado: Boolean? = null,
    val mensaje: String? = null,
    val tipo: String? = null
) {
    companion object {
        fun desdeJson(json: String): RespuestaWS {
            val o = JSONObject(json)

            return RespuestaWS(
                ok = o.optBoolean("ok", false),
                rostroDetectado = o.optBoolean("rostro_detectado", false),
                mensaje = o.optString("mensaje", null),
                tipo = o.optString("tipo", null)
            )
        }
    }
}
