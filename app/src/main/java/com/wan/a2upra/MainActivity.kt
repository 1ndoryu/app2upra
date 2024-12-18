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

import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.volley.RequestQueue
import kotlinx.coroutines.delay
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


/////////////////////////////////////////
class MainActivity : ComponentActivity() {
    private lateinit var gestorDescargas: GestorDescargas
    private var vistaWeb: WebView? = null
    private lateinit var audioPlayer: AudioPlayer
    lateinit var mediaSession: MediaSessionCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaSession = MediaSessionCompat(this, "AudioPlayer")
        audioPlayer = AudioPlayer(this)
        window.setBackgroundDrawableResource(android.R.color.black) // Fondo negro
        window.statusBarColor = AndroidColor.parseColor("#050505")
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        gestorDescargas = GestorDescargas(this)
        setContent {
            PantallaWebView(
                gestorDescargas,
                { vistaWeb = it },
                { window.statusBarColor = AndroidColor.parseColor("#050505") },
                audioPlayer
            )
        }
    }

    override fun onBackPressed() {
        if (vistaWeb?.canGoBack() == true) {
            vistaWeb?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    fun showAudioNotification(
        title: String,
        author: String,
        imageUrl: String,
        audioSrc: String,
        mediaSession: MediaSessionCompat
    ) {
        audioPlayer.showAudioNotification(title, author, imageUrl, audioSrc, mediaSession)
    }

    fun updateNotificationPlaybackState(state: String, mediaSession: MediaSessionCompat) {
        audioPlayer.updateNotificationPlaybackState(state, mediaSession)
    }
}

@Composable
fun PantallaWebView(
    gestorDescargas: GestorDescargas,
    setVistaWeb: (WebView) -> Unit,
    configurarStatusBar: () -> Unit,
    audioPlayer: AudioPlayer
) {
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
                    AudioPlayerInterface(contexto),
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

class ConfiguradorWebView(
    private val gestorDescargas: GestorDescargas,
    private val userHelper: UserHelper,
    private val audioPlayer: AudioPlayer,
    private val context: Context
) {
    private val tag = "panjamon"

    fun configurar(vistaWeb: WebView) {
        configurarAjustes(vistaWeb)
        configurarClientes(vistaWeb)
        configurarListenerDescargas(vistaWeb)
        vistaWeb.loadUrl("https://2upra.com")
        vistaWeb.addJavascriptInterface(AudioPlayerInterface(context), "AndroidAudioPlayer")
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
        //Log.d(tag, "userId guardado en userHelper")
        FirebaseMessaging.getInstance().token.addOnCompleteListener { tarea ->
            if (tarea.isSuccessful) {
                val token = tarea.result
                //Log.d(tag, "Token de Firebase obtenido: $token")
                userHelper.enviarToken(token, userId)
                //Log.d(tag, "Token y userId enviados con userHelper")
            } else {
                Log.e(tag, "Error al obtener el token de Firebase", tarea.exception)
            }
        }
    }

    private inline fun conCookieManager(bloque: CookieManager.() -> Unit) {
        CookieManager.getInstance().apply(bloque)
    }
}

class AudioPlayer(private val context: Context) {
    private val notificationId = 1
    private var playbackState = "stopped"

    private fun getPendingIntentForAction(action: String): PendingIntent {
        val intent = Intent(context, AudioNotificationReceiver::class.java).apply {
            this.action = action // Ahora 'action' es accesible
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showAudioNotification(
        title: String,
        author: String,
        imageUrl: String,
        audioSrc: String,
        mediaSession: MediaSessionCompat
    ) {
        Log.d(
            "panjamon",
            "showAudioNotification: Mostrando notificación - Título: $title, Autor: $author, Estado: $playbackState, audioSrc: $audioSrc"
        )

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "audio_playback_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Reproducción de audio",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val playPauseAction = NotificationCompat.Action.Builder(
            if (playbackState == "playing") R.drawable.ic_pause else R.drawable.ic_play,
            if (playbackState == "playing") "Pausar" else "Reproducir",
            getPendingIntentForAction("TOGGLE_PLAY_PAUSE")
        ).build()

        val largeIcon = obtenerBitmapDeUrl(imageUrl)

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(author)
            .setSmallIcon(R.drawable.blanco)
            .setLargeIcon(largeIcon)
            .addAction(playPauseAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0)
                    .setMediaSession(mediaSession.sessionToken)
            )
            .setOngoing(playbackState == "playing")
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun updateNotificationPlaybackState(state: String, mediaSession: MediaSessionCompat) {
        Log.d("panjamon", "updateNotificationPlaybackState: Actualizando estado a: $state")
        playbackState = state
        Log.d(
            "panjamon",
            "updateNotificationPlaybackState: playbackState actualizado a: $playbackState"
        )
        showAudioNotification(
            "Reproduciendo ahora",
            "Artista",
            "",
            "",
            mediaSession
        )
    }

    private fun obtenerBitmapDeUrl(imageUrl: String): Bitmap? {
        return if (imageUrl.isNotEmpty()) {
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
    }
}

class AudioNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("panjamon", "AudioNotificationReceiver.onReceive: Acción recibida: ${intent.action}")
        when (intent.action) {
            "TOGGLE_PLAY_PAUSE" -> {
                Log.d("panjamon", "AudioNotificationReceiver.onReceive: Acción TOGGLE_PLAY_PAUSE")
                // Enviar un broadcast local
                val localIntent = Intent("TOGGLE_PLAY_PAUSE_ACTION")
                LocalBroadcastManager.getInstance(context).sendBroadcast(localIntent)
            }
        }
    }
}

class AudioPlayerInterface(private val context: Context) {
    @JavascriptInterface
    fun sendAudioInfo(title: String, author: String, imageUrl: String, audioSrc: String) {
        Log.d(
            "panjamon",
            "AudioPlayerInterface.sendAudioInfo: Título: $title, Autor: $author, Imagen: $imageUrl, AudioSrc: $audioSrc"
        )
        (context as MainActivity).showAudioNotification(
            title,
            author,
            imageUrl,
            audioSrc,
            (context as MainActivity).mediaSession
        )
    }

    @JavascriptInterface
    fun updateNotificationPlaybackState(state: String) {
        Log.d(
            "panjamon",
            "AudioPlayerInterface.updateNotificationPlaybackState: Estado recibido de WebView: $state"
        )
        (context as MainActivity).updateNotificationPlaybackState(
            state,
            (context as MainActivity).mediaSession
        )
    }

    @JavascriptInterface
    fun log(message: String) {
        Log.d("panjamon", "AudioPlayerInterface: $message")
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



