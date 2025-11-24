package com.example.appkalacam

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.exifinterface.media.ExifInterface
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.appkalacam.net.RetrofitClient
import com.example.appkalacam.net.UserCreatedResponse
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // VISTAS
    private lateinit var etNombre: TextInputEditText
    private lateinit var etApellido: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var previewView: PreviewView
    private lateinit var overlayGuia: View
    private lateinit var ivThumbnail: ImageView
    private lateinit var btnTomarFoto: Button
    private lateinit var btnCapturar: Button
    private lateinit var btnEnviar: Button
    private lateinit var btnCambiarCamara: Button
    private lateinit var btnGaleria: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    // ESTADO DE LA CÁMARA
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isFrontCamera = true
    private var photoFile: File? = null
    private var rostroValidado = false

    // REGISTRO DE ACTIVIDADES Y PERMISOS
    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            showStatus("Permiso de cámara denegado. No se puede usar la cámara.")
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val file = File.createTempFile("GALERIA_", ".jpg", cacheDir)
                contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    processNewImage(file, "Imagen seleccionada, validando rostro…")
                }
            }
        }
    }

    // --- CICLO DE VIDA ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupListeners()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }

        updateCapturarButtonState()
    }

    private fun bindViews() {
        etNombre = findViewById(R.id.etNombre)
        etApellido = findViewById(R.id.etApellido)
        etEmail = findViewById(R.id.etEmail)
        previewView = findViewById(R.id.previewView)
        overlayGuia = findViewById(R.id.overlayGuia)
        ivThumbnail = findViewById(R.id.ivThumbnail)
        btnTomarFoto = findViewById(R.id.btnTomarFoto)
        btnCapturar = findViewById(R.id.btnCapturar)
        btnEnviar = findViewById(R.id.btnEnviar)
        btnCambiarCamara = findViewById(R.id.btnCambiarCamara)
        btnGaleria = findViewById(R.id.btnGaleria)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun setupListeners() {
        btnTomarFoto.setOnClickListener { startCamera() }
        btnCapturar.setOnClickListener { capturePhoto() }
        btnEnviar.setOnClickListener { submitForm() }
        btnGaleria.setOnClickListener { pickImage.launch("image/*") }
        btnCambiarCamara.setOnClickListener {
            isFrontCamera = !isFrontCamera
            // Desvincula y Reinicia para liberar recursos y reconfigurar
            cameraProvider?.unbindAll()
            startCamera()
            showStatus("Cambiando cámara a ${if (isFrontCamera) "frontal" else "trasera"}…")
        }
    }

    // --- LÓGICA DE CÁMARA (Optimizado para evitar retención de recursos) ---

    private fun startCamera() {
        showCameraUi()
        rostroValidado = false
        updateCapturarButtonState()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Desvinculación aquí antes de reconfigurar (Crítico para liberar superficies antiguas)
            cameraProvider?.unbindAll()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(previewView.display.rotation)
                .setJpegQuality(90)
                .build()

            val selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.bindToLifecycle(this, selector, preview, imageCapture)
                showStatus("Cámara lista y vinculada.")
            } catch (exc: Exception) {
                showStatus("Error al vincular la cámara: ${exc.localizedMessage}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        showStatus("Capturando foto…")

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())
        val file = File.createTempFile("SELFIE_$timeStamp", ".jpg", cacheDir)
        val output = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(output, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                showStatus("Error al capturar: ${exc.message}")
            }

            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                processNewImage(file, "Foto capturada, validando rostro…")
            }
        })
    }

    private fun processNewImage(file: File, initialStatus: String) {
        photoFile = file
        rostroValidado = false
        // Se desvincula la cámara inmediatamente después de la captura exitosa
        // para asegurar que los buffers de Preview no interfieran con el ImageCapture
        cameraProvider?.unbindAll()
        mostrarFotoConBorde(file, false)
        showStatus(initialStatus)
        validarRostroConWebSocket(file)
    }

    // --- UI Y ESTADO DE VALIDACIÓN ---

    private fun mostrarFotoConBorde(file: File, bordeVerde: Boolean) {
        val bmp = BitmapFactory.decodeFile(file.absolutePath)
        ivThumbnail.setImageBitmap(bmp)

        ivThumbnail.visibility = View.VISIBLE
        overlayGuia.visibility = View.VISIBLE
        overlayGuia.setBackgroundResource(
            if (bordeVerde) R.drawable.borde_verde else R.drawable.borde_rojo
        )

        previewView.visibility = View.GONE
        btnCapturar.visibility = View.GONE
        btnCambiarCamara.visibility = View.GONE
        btnTomarFoto.visibility = View.VISIBLE
    }

    private fun showCameraUi() {
        btnTomarFoto.visibility = View.GONE
        btnCapturar.visibility = View.VISIBLE
        btnCambiarCamara.visibility = View.VISIBLE
        previewView.visibility = View.VISIBLE
        overlayGuia.visibility = View.VISIBLE
        ivThumbnail.visibility = View.GONE
    }

    private fun updateCapturarButtonState() {
        btnEnviar.isEnabled = rostroValidado && photoFile != null
        btnCapturar.isEnabled = !rostroValidado
    }

    // --- LÓGICA DE VALIDACIÓN (WebSocket) ---

    private fun corregirOrientacionImagen(file: File): Bitmap {
        /**
         * Corrige la orientación de la imagen basándose en los datos EXIF.
         * También maneja el espejo de la cámara frontal.
         */
        var bitmap = BitmapFactory.decodeFile(file.absolutePath)
        
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            val matrix = Matrix()
            
            // Aplicar rotación según EXIF
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            }
            
            // Para cámara frontal, NO aplicar espejo
            // (La imagen ya viene correcta de ImageCapture)
            
            // Aplicar transformación si es necesaria
            if (!matrix.isIdentity) {
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = rotatedBitmap
                }
            }
        } catch (e: Exception) {
            // Si hay error leyendo EXIF, usar imagen original
            android.util.Log.e("MainActivity", "Error leyendo EXIF: ${e.message}")
        }
        
        return bitmap
    }

    private fun validarRostroConWebSocket(file: File) {
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url("wss://iot-backend-production-4413.up.railway.app/ws/validarRostro")
            .build()

        val wsListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        // Corregir orientación de la imagen
                        var bmp = corregirOrientacionImagen(file)
                        
                        // Redimensionar a 640x480
                        val targetWidth = 640
                        val targetHeight = 480
                        val scaled = Bitmap.createScaledBitmap(bmp, targetWidth, targetHeight, true)
                        
                        // Comprimir a JPEG
                        val stream = java.io.ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                        val bytes = stream.toByteArray()
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        
                        // Liberar recursos
                        if (bmp != scaled) bmp.recycle()
                        scaled.recycle()
                        
                        // Enviar en formato JSON que espera el backend
                        val jsonPayload = """{"tipo": "imagen", "imagen": "$base64", "content_type": "image/jpeg"}"""
                        webSocket.send(jsonPayload)
                    } catch (e: Exception) {
                        onErrorValidation("Error de procesamiento de imagen: ${e.message}")
                        webSocket.close(1000, "Error de procesamiento local")
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        val json = org.json.JSONObject(text)
                        
                        // Ignorar mensajes de keepalive y conexión
                        val tipo = json.optString("tipo", "")
                        if (tipo == "keepalive" || tipo == "conexion") {
                            return@launch
                        }
                        
                        // Leer la respuesta de validación
                        val ok = json.optBoolean("ok", false)
                        val rostroDetectado = json.optBoolean("rostro_detectado", false)
                        val mensaje = json.optString("mensaje", "Sin mensaje")
                        
                        val isValid = ok && rostroDetectado
                        val statusMsg = if (isValid) "Rostro válido ✔" else "Rostro no válido ❌: $mensaje"
                        
                        onValidationResult(isValid, statusMsg)
                        webSocket.close(1000, null)
                    } catch (e: Exception) {
                        onErrorValidation("Error al parsear respuesta: ${e.message}")
                        webSocket.close(1000, "Error de parseo")
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onErrorValidation("Error de red/WebSocket: ${t.localizedMessage ?: "Desconocido"}")
            }

            private fun onErrorValidation(message: String) {
                lifecycleScope.launch(Dispatchers.Main) {
                    onValidationResult(false, message)
                }
            }
        }

        client.newWebSocket(request, wsListener)
    }

    private fun onValidationResult(isValid: Boolean, statusMessage: String) {
        rostroValidado = isValid
        // Usar '!!' es seguro aquí porque 'processNewImage' garantiza que photoFile no es nulo.
        mostrarFotoConBorde(photoFile!!, isValid)
        showStatus(statusMessage)
        updateCapturarButtonState()
    }

    // --- LÓGICA DE ENVÍO (Retrofit) ---

    private fun submitForm() {
        val nombre = etNombre.text.toString().trim()
        val apellido = etApellido.text.toString().trim()
        val email = etEmail.text.toString().trim()

        if (!validateFields(nombre, apellido, email)) return
        val file = photoFile ?: run { snack("Error: Archivo de foto no encontrado"); return }

        val rbNombre = nombre.toRequestBody("text/plain".toMediaType())
        val rbApellido = apellido.toRequestBody("text/plain".toMediaType())
        val rbEmail = email.toRequestBody("text/plain".toMediaType())
        val mime = "image/jpeg".toMediaTypeOrNull()
        val imgBody = file.asRequestBody(mime)
        val part = MultipartBody.Part.createFormData("imagen", file.name, imgBody)

        progressBar.visibility = View.VISIBLE
        btnEnviar.isEnabled = false
        showStatus("Enviando datos…")

        lifecycleScope.launch(Dispatchers.IO) {
            val resp: retrofit2.Response<UserCreatedResponse> = try {
                RetrofitClient.api.subirUsuario(rbNombre, rbApellido, rbEmail, part)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleSubmissionError("Error de red: ${e.localizedMessage}")
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                btnEnviar.isEnabled = true
                if (resp.isSuccessful) {
                    successDialog("✅ Sesión iniciada correctamente\nBienvenido, $nombre")
                    clearForm()
                } else {
                    handleSubmissionError("Error del servidor (${resp.code()})")
                }
            }
        }
    }

    private fun validateFields(nombre: String, apellido: String, email: String): Boolean {
        if (nombre.isEmpty()) { etNombre.error = "Ingresa tu nombre"; etNombre.requestFocus(); return false }
        if (apellido.isEmpty()) { etApellido.error = "Ingresa tu apellido"; etApellido.requestFocus(); return false }
        if (email.isEmpty()) { etEmail.error = "Ingresa tu correo"; etEmail.requestFocus(); return false }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Correo inválido"; etEmail.requestFocus(); return false
        }
        return true
    }

    private fun handleSubmissionError(message: String) {
        progressBar.visibility = View.GONE
        btnEnviar.isEnabled = true
        snack(message)
    }

    // --- UTILIDADES DE UI Y LIMPIEZA FINAL ---

    private fun showStatus(msg: String) { tvStatus.text = msg }
    private fun snack(msg: String) { Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG).show(); showStatus(msg) }
    private fun successDialog(message: String) { AlertDialog.Builder(this).setTitle("Éxito").setMessage(message).setPositiveButton("OK", null).show() }

    private fun clearForm() {
        etNombre.setText("")
        etApellido.setText("")
        etEmail.setText("")
        ivThumbnail.setImageDrawable(null)
        ivThumbnail.visibility = View.GONE
        overlayGuia.visibility = View.GONE

        // **Limpieza de recursos:** Se libera la referencia al archivo y se desvincula la cámara
        photoFile = null
        rostroValidado = false
        cameraProvider?.unbindAll()

        updateCapturarButtonState()
        showStatus("")

        btnTomarFoto.visibility = View.VISIBLE
        btnCapturar.visibility = View.GONE
        btnCambiarCamara.visibility = View.GONE
        previewView.visibility = View.GONE
    }
}