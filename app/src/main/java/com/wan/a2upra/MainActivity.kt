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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PantallaWebView(
    gestorDescargas: GestorDescargas,
    setVistaWeb: (WebView) -> Unit,
    configurarStatusBar: () -> Unit
) {
    val contexto = LocalContext.current
    val configuradorWebView = ConfiguradorWebView(gestorDescargas)
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


@Composable
fun ContenedorWebView(vistaWeb: WebView) {
    AndroidView(factory = { vistaWeb })
}
/////////////////////////////////////////

class ConfiguradorWebView(private val gestorDescargas: GestorDescargas) {
    fun configurar(vistaWeb: WebView) {
        configurarAjustes(vistaWeb)
        vistaWeb.webViewClient = crearWebViewClient()
        vistaWeb.webChromeClient = WebChromeClient()
        vistaWeb.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            gestorDescargas.manejarDescarga(
                url,
                userAgent,
                contentDisposition,
                mimetype,
                contentLength
            )
        }
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
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(vistaWeb, true)
        }
    }

    private fun crearWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                if (!esUrlPermitida(url)) {
                    return true
                }
                return false
            }
        }
    }

    private fun esUrlPermitida(url: String): Boolean {
        val dominiosPermitidos = listOf(
            "accounts.google.com",
            "google.com",
            "2upra.com",
            "checkout.stripe.com",
            ".stripe.com"
        )
        return dominiosPermitidos.any { url.contains(it) }
    }
}

class UserHelper(private val context: Context) {

    fun sendTokenToWordPress(token: String?, userId: String) {
        val url = "https://2upra.com/wp-json/custom/v1/save-token"
        val requestQueue = Volley.newRequestQueue(context)

        Log.d("panjamon", "sendTokenToWordPress: Iniciando envío de token")

        // Obtenemos la versión actual de la app
        val appVersionName = getAppVersionName()
        val appVersionCode = getAppVersionCode()

        Log.d("panjamon", "sendTokenToWordPress: appVersionName = $appVersionName")
        Log.d("panjamon", "sendTokenToWordPress: appVersionCode = $appVersionCode")

        // Creamos el JSON que se enviará al servidor
        val jsonObject = JSONObject().apply {
            put("token", token ?: "") // Enviar cadena vacía si el token es nulo
            put("userId", userId)
            put("appVersionName", appVersionName)
            put("appVersionCode", appVersionCode)
        }

        Log.d("panjamon", "sendTokenToWordPress: JSON a enviar = $jsonObject")

        val jsonObjectRequest = object : JsonObjectRequest(
            Method.POST, url, jsonObject,
            { response ->
                // Manejar respuesta exitosa del servidor (opcional)
                Log.d("panjamon", "sendTokenToWordPress: Respuesta exitosa del servidor: $response")
                Log.d("UserHelper", "Token enviado con éxito: $response")
            },
            { error ->
                // Manejar error de la solicitud
                Log.e("panjamon", "sendTokenToWordPress: Error en la solicitud: ${error.message}")
                Log.e("UserHelper", "Error enviando el token: ${error.message}")

                // Detalles adicionales del error
                error.networkResponse?.let {
                    Log.e(
                        "panjamon",
                        "sendTokenToWordPress: Código de estado HTTP: ${it.statusCode}"
                    )
                    Log.e(
                        "panjamon",
                        "sendTokenToWordPress: Datos de la respuesta: ${String(it.data)}"
                    )
                }

                if (error.cause != null) {
                    Log.e("panjamon", "sendTokenToWordPress: Causa del error: ${error.cause}")
                }
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = hashMapOf("Content-Type" to "application/json")
                Log.d("panjamon", "sendTokenToWordPress: Headers = $headers")
                return headers
            }
        }

        requestQueue.add(jsonObjectRequest)
        Log.d("panjamon", "sendTokenToWordPress: Solicitud agregada a la cola")
    }

    fun saveUserId(userId: String) {
        Log.d("panjamon", "saveUserId: Guardando userId = $userId")
        context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit().apply {
            putString("userId", userId)
            apply()
        }
        Log.d("panjamon", "saveUserId: userId guardado correctamente")
    }

    // Función para obtener el nombre de la versión (ejemplo: "1.0.2")
    private fun getAppVersionName(): String {
        return try {
            val packageInfo: PackageInfo =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
            val versionName = packageInfo.versionName ?: "Unknown"
            Log.d("panjamon", "getAppVersionName: versionName = $versionName")
            versionName
        } catch (e: Exception) {
            Log.e("panjamon", "getAppVersionName: Error al obtener versionName: ${e.message}")
            "Unknown"
        }
    }

    // Función para obtener el código de la versión (ejemplo: 3)
    private fun getAppVersionCode(): Int {
        return try {
            val packageInfo: PackageInfo =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
            val versionCode = packageInfo.versionCode
            Log.d("panjamon", "getAppVersionCode: versionCode = $versionCode")
            versionCode
        } catch (e: Exception) {
            Log.e("panjamon", "getAppVersionCode: Error al obtener versionCode: ${e.message}")
            -1 // Retornar -1 en caso de error
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


