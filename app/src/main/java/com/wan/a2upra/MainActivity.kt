package com.wan.a2upra

import android.app.DownloadManager
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import com.android.volley.toolbox.JsonObjectRequest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.os.Build
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.support.v4.media.session.MediaSessionCompat
import android.Manifest
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.volley.RequestQueue
import kotlinx.coroutines.delay
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.bumptech.glide.Glide
import android.webkit.JavascriptInterface
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.IntentFilter
import androidx.compose.material3.CircularProgressIndicator
import androidx.core.content.ContextCompat


/////////////////////////////////////////
class MainActivity : ComponentActivity() {
    private lateinit var gestorDescargas: GestorDescargas
    private var vistaWeb: WebView? = null
    private lateinit var audioPlayer: AudioPlayer
    private var permisosConcedidos = false
    private lateinit var mediaSession: MediaSessionCompat

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        permisosConcedidos = isGranted
        if (isGranted) {
            inicializarAudioPlayer()
            Toast.makeText(this, "Permiso para notificaciones concedido", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permiso para notificaciones denegado", Toast.LENGTH_SHORT).show()
        }
        // Forzar la recomposición después de manejar los permisos
        setContent {
            PantallaPrincipal()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaSession = MediaSessionCompat(this, "AudioPlayer")
        audioPlayer = AudioPlayer(this, mediaSession)
        inicializarApariencia()
        permisosNotificacion()
        gestorDescargas = GestorDescargas(this)
        setContent {
            PantallaPrincipal()
        }
    }

    @Composable
    private fun PantallaPrincipal() {
        if (permisosConcedidos) {
            PantallaWebView(
                gestorDescargas,
                { vistaWeb = it },
                { window.statusBarColor = AndroidColor.parseColor("#050505") },
                audioPlayer  // Usar '!!' solo si estás seguro de que no es null
            )
        } else {
            // Muestra una pantalla de carga o un indicador hasta que se manejen los permisos
            CircularProgressIndicator()
        }
    }

    private fun inicializarAudioPlayer() {
        if (audioPlayer == null) {
            audioPlayer = AudioPlayer(this, mediaSession)
        }
    }

    private fun inicializarApariencia() {
        window.setBackgroundDrawableResource(android.R.color.black)
        window.statusBarColor = AndroidColor.parseColor("#050505")
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    }

    private fun permisosNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                permisosConcedidos = true
                inicializarAudioPlayer()
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                Toast.makeText(
                    this,
                    "Necesitamos permiso para mostrar notificaciones de reproducción",
                    Toast.LENGTH_SHORT
                ).show()
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Para versiones anteriores a Android 13, no se necesitan permisos especiales
            permisosConcedidos = true
            inicializarAudioPlayer()
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
fun PantallaWebView(gestorDescargas: GestorDescargas, setVistaWeb: (WebView) -> Unit, configurarStatusBar: () -> Unit, audioPlayer: AudioPlayer) {
    val contexto = LocalContext.current
    val configuradorWebView =
        ConfiguradorWebView(gestorDescargas, UserHelper(contexto), audioPlayer, contexto)
    var vistaWeb by remember { mutableStateOf<WebView?>(null) }
    var mostrarLogo by remember { mutableStateOf(true) }

    if (mostrarLogo) {
        MostrarLogo()
    }

    LaunchedEffect(Unit) {
        delay(100)
        mostrarLogo = false
    }

    if (!mostrarLogo) {
        val nuevaVistaWeb = remember(contexto) {
            WebView(contexto).apply {
                configuradorWebView.configurar(this)
                setBackgroundColor(AndroidColor.parseColor("#050505"))
                configurarStatusBar()
                setVistaWeb(this)
                addJavascriptInterface(
                    AudioPlayerInterface(audioPlayer),
                    "AndroidAudioPlayer"
                )
            }
        }
        vistaWeb = nuevaVistaWeb

        VistaWebConDeslizarParaRefrescar(nuevaVistaWeb) {
            vistaWeb = it
        }
    }
}

class ConfiguradorWebView(private val gestorDescargas: GestorDescargas, private val userHelper: UserHelper, private val audioPlayer: AudioPlayer, private val context: Context) {
    private val tag = "panjamon"

    fun configurar(vistaWeb: WebView) {
        configurarAjustes(vistaWeb)
        configurarClientes(vistaWeb)
        configurarListenerDescargas(vistaWeb)
        vistaWeb.loadUrl("https://2upra.com")
        vistaWeb.addJavascriptInterface(
            AudioPlayerInterface(audioPlayer),
            "AndroidAudioPlayer"
        )
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
            return !esUrlPermitida(url)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
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
            if (resultado != null && resultado != "null") {
                val userId = resultado.replace("\"", "")
                manejarUserId(userId)
            }
        }
    }

    private fun manejarUserId(userId: String) {
        userHelper.guardarUserId(userId)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { tarea ->
            if (tarea.isSuccessful) {
                val token = tarea.result
                userHelper.enviarToken(token, userId)
            } else {
                Log.e(tag, "Error al obtener el token de Firebase", tarea.exception)
            }
        }
    }

    private inline fun conCookieManager(bloque: CookieManager.() -> Unit) {
        CookieManager.getInstance().apply(bloque)
    }
}

class AudioPlayer(private val context: Context, private val mediaSession: MediaSessionCompat) {
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "audio_playback_channel"
    }

    private var playbackState = "stopped"
    private var currentTitle = ""
    private var currentAuthor = ""
    private var currentImageUrl = ""
    private var currentAudioSrc = ""

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        crearCanalNotificacion()
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Reproducción de audio",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    private fun obtenerPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AudioNotificationReceiver::class.java).apply {
                action = "TOGGLE_PLAY_PAUSE"
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun mostrarNotificacion() {
        notificationManager.notify(NOTIFICATION_ID, crearNotificacion())
    }

    private fun crearNotificacion() =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentAuthor)
            .setSmallIcon(R.drawable.blanco)
            .setLargeIcon(obtenerBitmapDeUrl(currentImageUrl))
            .addAction(crearAccionReproduccion())
            .setStyle(crearEstiloMultimedia())
            .setOngoing(playbackState == "playing")
            .build()

    private fun crearAccionReproduccion() =
        NotificationCompat.Action.Builder(
            if (playbackState == "playing") R.drawable.ic_pause else R.drawable.ic_play,
            if (playbackState == "playing") "Pausar" else "Reproducir",
            obtenerPendingIntent()
        ).build()

    private fun crearEstiloMultimedia() =
        androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0)
            .setMediaSession(mediaSession.sessionToken)

    fun actualizarEstadoReproduccion(state: String) {
        playbackState = state
        mostrarNotificacion()
    }

    fun togglePlayPause() {
        playbackState = if (playbackState == "playing") "paused" else "playing"
        actualizarEstadoReproduccion(playbackState)
    }

    private fun obtenerBitmapDeUrl(imageUrl: String): Bitmap? =
        if (imageUrl.isNotEmpty()) {
            try {
                Glide.with(context)
                    .asBitmap()
                    .load(imageUrl)
                    .override(128, 128)
                    .submit()
                    .get()
            } catch (e: Exception) {
                Log.e("panjamon", "Error al obtener bitmap de Glide", e)
                null
            }
        } else {
            null
        }

    fun actualizarInfoAudio(
        title: String,
        author: String,
        imageUrl: String,
        audioSrc: String
    ) {
        currentTitle = title
        currentAuthor = author
        currentImageUrl = imageUrl
        currentAudioSrc = audioSrc
    }
}

class AudioNotificationReceiver(private val audioPlayer: AudioPlayer? = null) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (audioPlayer == null) {
            Log.e("panjamon", "AudioPlayer is null in AudioNotificationReceiver")
            return
        }

        when (intent.action) {
            "TOGGLE_PLAY_PAUSE" -> audioPlayer.togglePlayPause()
        }
    }
}

class AudioPlayerInterface(private val audioPlayer: AudioPlayer) {
    @JavascriptInterface
    fun sendAudioInfo(title: String, author: String, imageUrl: String, audioSrc: String) {
        audioPlayer.actualizarInfoAudio(title, author, imageUrl, audioSrc)
        audioPlayer.mostrarNotificacion()
    }

    @JavascriptInterface
    fun updateNotificationPlaybackState(state: String) {
        audioPlayer.actualizarEstadoReproduccion(state)
    }

    @JavascriptInterface
    fun log(message: String) {
        Log.d("panjamon", message)
    }
}

@Composable
fun MostrarLogo() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            //y aca obviamente falla si no elijo import androidx.compose.ui.graphics.Color
            .background(ComposeColor(0xFF050505)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.blanco),
            contentDescription = "Logo",
            modifier = Modifier.size(60.dp)
        )
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
                //Log.d("UserHelper", "Token enviado con éxito: $response")
            },
            { error ->
                //Log.e("UserHelper", "Error enviando el token: ${error.message}")
                error.networkResponse?.let {
                    //Log.e("UserHelper", "Código de estado HTTP: ${it.statusCode}")
                    //Log.e("UserHelper", "Datos de la respuesta: ${String(it.data)}")
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

@Composable
fun VistaWebConDeslizarParaRefrescar(vistaWeb: WebView, actualizarVistaWeb: (WebView) -> Unit) {
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



