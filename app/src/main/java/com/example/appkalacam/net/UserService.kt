package com.example.appkalacam.net

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

// ✅ Esta data class es la que MainActivity necesita
data class UserCreatedResponse(
    val ok: Boolean? = null,
    val message: String? = null,
    val usuario: Any? = null
)

interface UserService {

    @Multipart
    @POST("/subirUsuario")
    suspend fun subirUsuario(
        @Part("nombre") nombre: RequestBody,
        @Part("apellido") apellido: RequestBody,
        @Part("email") email: RequestBody,
        @Part imagen: MultipartBody.Part
    ): Response<UserCreatedResponse>

}
