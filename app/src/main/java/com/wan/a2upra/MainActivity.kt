package com.wan.a2upra

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.messaging.FirebaseMessaging
import com.wan.a2upra.ui.theme._2upraTheme
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.android.volley.toolbox.Volley
import com.google.firebase.FirebaseApp
import org.json.JSONObject
import com.android.volley.toolbox.JsonObjectRequest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import android.os.Build
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.MotionEvent
import android.view.View
import android.content.pm.PackageInfo
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import com.android.volley.RequestQueue

/////////////////////////////////////////
class MainActivity : ComponentActivity() {
    private lateinit var gestorDescargas: GestorDescargas
    private var vistaWeb: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        gestorDescargas = GestorDescargas(this)
        setContent {
            PantallaWebView(gestorDescargas, { vistaWeb = it }) {
                window.statusBarColor = Color.parseColor("#050505")
            }
        }
    }

    override fun onBackPressed() {
        if (vistaWeb?.canGoBack() == true) {
            vistaWeb?.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

@Composable
fun PantallaWebView(
    gestorDescargas: GestorDescargas,
    setVistaWeb: (WebView) -> Unit,
    configurarStatusBar: () -> Unit
) {
    val contexto = LocalContext.current
    val userHelper = UserHelper(contexto)
    val configuradorWebView = ConfiguradorWebView(gestorDescargas, userHelper)
    var vistaWeb by remember { mutableStateOf<WebView?>(null) }

    val nuevaVistaWeb = remember(contexto) {
        WebView(contexto).apply {
            configuradorWebView.configurar(this)
            configurarStatusBar()
            setVistaWeb(this)
        }
    }
    vistaWeb = nuevaVistaWeb

    VistaWebConDeslizarParaRefrescar(nuevaVistaWeb) {
        vistaWeb = it
    }
}
///////////////////////////////////////////////////


class ConfiguradorWebView(
    private val gestorDescargas: GestorDescargas,
    private val userHelper: UserHelper
) {
    private val tag = "panjamon"

    fun configurar(vistaWeb: WebView) {
        configurarAjustes(vistaWeb)
        configurarClientes(vistaWeb)
        configurarListenerDescargas(vistaWeb)
        vistaWeb.loadUrl("https://2upra.com")
    }

    private fun configurarAjustes(vistaWeb: WebView) {
        vistaWeb.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            userAgentString =
                "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Mobile Safari/537.36 AppAndroid"
            conCookieManager {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(vistaWeb, true)
            }
        }
    }

    private fun configurarClientes(vistaWeb: WebView) {
        vistaWeb.webViewClient = crearWebViewClient()
        vistaWeb.webChromeClient = WebChromeClient()
    }

    private fun configurarListenerDescargas(vistaWeb: WebView) {
        vistaWeb.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            gestorDescargas.manejarDescarga(
                url,
                userAgent,
                contentDisposition,
                mimetype,
                contentLength
            )
        }
    }

    private fun crearWebViewClient() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            val url = request.url.toString()
            Log.d(tag, "URL cargando: $url")
            val permitida = esUrlPermitida(url)
            Log.d(tag, "URL permitida: $permitida")
            return !permitida
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Log.d(tag, "Página finalizada: $url")
            inyectarJavascript(view)
            (view?.parent as? SwipeRefreshLayout)?.isRefreshing = false
        }
    }

    private fun esUrlPermitida(url: String) = listOf(
        "accounts.google.com",
        "google.com",
        "2upra.com",
        "checkout.stripe.com",
        ".stripe.com"
    ).any { url.contains(it) }

    private fun inyectarJavascript(vistaWeb: WebView?) {
        val script = "(function() { " +
                "if (window.userId) { " +
                "var idUsuario = window.userId; " +
                "return idUsuario; " +
                "} else { " +
                "return null;" +
                "}" +
                "})();"
        vistaWeb?.evaluateJavascript(script) { resultado ->
            Log.d(tag, "Resultado de inyectarJavascript: $resultado")
            if (resultado != null && resultado != "null") {
                val userId = resultado.replace("\"", "")
                Log.d(tag, "userId obtenido: $userId")
                manejarUserId(userId)
            } else {
                Log.d(tag, "userId no encontrado en la página")
            }
        }
    }

    private fun manejarUserId(userId: String) {
        userHelper.guardarUserId(userId)
        Log.d(tag, "userId guardado en userHelper")
        FirebaseMessaging.getInstance().token.addOnCompleteListener { tarea ->
            if (tarea.isSuccessful) {
                val token = tarea.result
                Log.d(tag, "Token de Firebase obtenido: $token")
                userHelper.enviarToken(token, userId)
                Log.d(tag, "Token y userId enviados con userHelper")
            } else {
                Log.e(tag, "Error al obtener el token de Firebase", tarea.exception)
            }
        }
    }

    private inline fun conCookieManager(bloque: CookieManager.() -> Unit) {
        CookieManager.getInstance().apply(bloque)
    }
}

class UserHelper(private val context: Context) {

    fun enviarToken(token: String?, userId: String) {
        val url = "https://2upra.com/wp-json/custom/v1/save-token"
        val colaPeticiones = Volley.newRequestQueue(context)

        val json = crearJSON(token, userId)
        val peticion = crearPeticion(url, json, colaPeticiones)
        colaPeticiones.add(peticion)
    }

    fun guardarUserId(userId: String) {
        context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit().apply {
            putString("userId", userId)
            apply()
        }
    }

    private fun obtenerVersionNombre(): String {
        return try {
            val infoPaquete = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            infoPaquete.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun obtenerVersionCodigo(): Int {
        return try {
            val infoPaquete = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            infoPaquete.versionCode
        } catch (e: Exception) {
            -1
        }
    }

    private fun crearJSON(token: String?, userId: String): JSONObject {
        return JSONObject().apply {
            put("token", token ?: "")
            put("userId", userId)
            put("appVersionName", obtenerVersionNombre())
            put("appVersionCode", obtenerVersionCodigo())
        }
    }

    private fun crearPeticion(
        url: String,
        json: JSONObject,
        colaPeticiones: RequestQueue
    ): JsonObjectRequest {
        return object : JsonObjectRequest(
            Method.POST, url, json,
            { response ->
                Log.d("UserHelper", "Token enviado con éxito: $response")
            },
            { error ->
                Log.e("UserHelper", "Error enviando el token: ${error.message}")
                error.networkResponse?.let {
                    Log.e("UserHelper", "Código de estado HTTP: ${it.statusCode}")
                    Log.e("UserHelper", "Datos de la respuesta: ${String(it.data)}")
                }
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return mutableMapOf("Content-Type" to "application/json")
            }
        }
    }
}

class GestorDescargas(private val contexto: Context) {
    fun manejarDescarga(
        url: String,
        agenteUsuario: String,
        disposicionContenido: String,
        tipoMime: String,
        longitudContenido: Long
    ) {
        val solicitud = DownloadManager.Request(Uri.parse(url))
        val nombreArchivo = URLUtil.guessFileName(url, disposicionContenido, tipoMime)
        solicitud.setMimeType(tipoMime)
        solicitud.addRequestHeader("User-Agent", agenteUsuario)
        solicitud.setDescription("Descargando archivo...")
        solicitud.setTitle(nombreArchivo)
        solicitud.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        solicitud.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
        try {
            solicitud.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                nombreArchivo
            )
        } catch (e: IllegalStateException) {
            Toast.makeText(contexto, "Error: No se pudo guardar el archivo.", Toast.LENGTH_LONG)
                .show()
            return
        }
        val gestorDescargas = contexto.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        if (gestorDescargas == null) {
            Toast.makeText(contexto, "Error: No se pudo iniciar la descarga.", Toast.LENGTH_LONG)
                .show()
            return
        }
        val consulta = DownloadManager.Query()
        consulta.setFilterByStatus(DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_PAUSED)
        val cursor = gestorDescargas.query(consulta)
        var descargaEnCola = false
        while (cursor.moveToNext()) {
            val columnaIndice = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (columnaIndice != -1) {
                val uriLocal = cursor.getString(columnaIndice)
                if (uriLocal != null) {
                    val nombreArchivoLocal = Uri.parse(uriLocal).lastPathSegment
                    if (nombreArchivoLocal != null && nombreArchivoLocal.endsWith(nombreArchivo)) {
                        descargaEnCola = true
                        break
                    }
                }
            }
        }
        cursor.close()
        if (descargaEnCola) {
            Toast.makeText(
                contexto,
                "Descarga con el mismo nombre ya iniciada o en espera...",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        try {
            gestorDescargas.enqueue(solicitud)
        } catch (e: Exception) {
            Toast.makeText(
                contexto,
                "Error al iniciar la descarga: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        Toast.makeText(contexto, "Descarga iniciada...", Toast.LENGTH_LONG).show()
    }
}

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val targetUrl = remoteMessage.data["url"]
        showNotification(
            remoteMessage.notification?.title,
            remoteMessage.notification?.body,
            targetUrl
        )
    }

    private fun showNotification(title: String?, body: String?, targetUrl: String?) {
        val channelId = "default_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            targetUrl?.let { putExtra("target_url", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.blanco)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(0, notification)
    }
}

//Auxiliares
@Composable
fun ContenedorWebView(vistaWeb: WebView) {
    AndroidView(factory = { vistaWeb })
}

@Composable
fun VistaWebConDeslizarParaRefrescar(
    vistaWeb: WebView,
    actualizarVistaWeb: (WebView) -> Unit
) {
    var refrescando by remember { mutableStateOf(false) }
    var inhabilitarDeslizarRefrescar by remember { mutableStateOf(false) }
    var inicioY by remember { mutableStateOf(0f) }
    var inicioX by remember { mutableStateOf(0f) }
    var desplazando by remember { mutableStateOf(false) }

    AndroidView(factory = { contexto ->
        SwipeRefreshLayout(contexto).apply {
            setOnRefreshListener {
                vistaWeb.reload()
                refrescando = true
            }

            vistaWeb.setOnTouchListener { vista, evento ->
                when (evento.action) {
                    MotionEvent.ACTION_DOWN -> {
                        inicioY = evento.y
                        inicioX = evento.x
                        desplazando = false
                        vistaWeb.evaluateJavascript(
                            "(function() { " +
                                    "  var element = document.elementFromPoint(${inicioX.toInt()}, ${inicioY.toInt()});" +
                                    "  if (element) {" +
                                    "    while (element !== null) {" +
                                    "      if (element.classList.contains('no-refresh')) {" +
                                    "        return true;" +
                                    "      }" +
                                    "      element = element.parentElement;" +
                                    "    }" +
                                    "  }" +
                                    "  return false;" +
                                    "})();"
                        ) { resultado ->
                            inhabilitarDeslizarRefrescar = resultado == "true"
                        }
                        vista.parent.requestDisallowInterceptTouchEvent(false)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val diferenciaY = evento.y - inicioY
                        val diferenciaX = evento.x - inicioX

                        if (kotlin.math.abs(diferenciaY) > kotlin.math.abs(diferenciaX)) {
                            if (!desplazando) {
                                desplazando = true

                                if (diferenciaY > 0 && inhabilitarDeslizarRefrescar) {
                                    this.isEnabled = false
                                    vista.parent.requestDisallowInterceptTouchEvent(true)
                                } else {
                                    this.isEnabled = true
                                    vista.parent.requestDisallowInterceptTouchEvent(false)
                                }
                            } else if (inhabilitarDeslizarRefrescar) {
                                if (diferenciaY > 0) {
                                    vista.parent.requestDisallowInterceptTouchEvent(true)
                                } else {
                                    vista.parent.requestDisallowInterceptTouchEvent(false)
                                }
                            }
                        } else {
                            desplazando = true
                            vista.parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        this.isEnabled = true
                        inhabilitarDeslizarRefrescar = false
                        desplazando = false
                        vista.parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }
            addView(vistaWeb)
            actualizarVistaWeb(vistaWeb)
        }
    }, update = {
        it.isRefreshing = refrescando
    })
}



