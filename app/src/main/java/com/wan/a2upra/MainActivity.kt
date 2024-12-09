package com.wan.a2upra

import android.annotation.SuppressLint
import android.app.Activity
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
import com.android.volley.Request
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
import android.graphics.Color
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.MotionEvent
import android.view.View

/*
esto esto pasa con un solo click
2024-12-09 01:29:57.329 22605-22605 panjamon                com.wan.a2upra                       D  WebViewClient: onPageFinished: https://2upra.com/mu/
2024-12-09 01:29:57.356 22605-22757 panjamon                com.wan.a2upra                       D  AudioPlayerInterface: panjamon: Interfaz Android detectada
2024-12-09 01:29:57.359 22605-22757 panjamon                com.wan.a2upra                       D  AudioPlayerInterface: panjamon: Función updateNotificationPlaybackState NO encontrada
2024-12-09 01:30:09.167 22605-22757 panjamon                com.wan.a2upra                       D  AudioPlayerInterface.sendAudioInfo: Título: Test, Autor: Wandoriuss, Imagen: https://i0.wp.com/2upra.com/wp-content/uploads/2024/12/2e1555f4065271953df596891c3302cd-1.jpg?quality=40&strip=all, AudioSrc: https://2upra.com/wp-json/1/v1/2?token=estoy omitiendo el token
2024-12-09 01:30:09.167 22605-22757 panjamon                com.wan.a2upra                       D  showAudioNotification: Mostrando notificación - Título: Test, Autor: Wandoriuss, Estado: playing, audioSrc: https://2upra.com/wp-json/1/v1/2?token=
2024-12-09 01:30:09.231 22605-22757 panjamon                com.wan.a2upra                       D  AudioPlayerInterface.sendAudioInfo: Título: Título Desconocido, Autor: Autor Desconocido, Imagen: , AudioSrc: https://2upra.com/wp-json/1/v1/2?token=
2024-12-09 01:30:09.231 22605-22757 panjamon                com.wan.a2upra                       D  showAudioNotification: Mostrando notificación - Título: Título Desconocido, Autor: Autor Desconocido, Estado: playing, audioSrc: https://2upra.com/wp-json/1/v1/2?token=
2024-12-09 01:30:09.242 22605-22757 panjamon                com.wan.a2upra                       D  AudioPlayerInterface.updateNotificationPlaybackState: Estado recibido de WebView: playing
2024-12-09 01:30:09.242 22605-22757 panjamon                com.wan.a2upra                       D  updateNotificationPlaybackState: Actualizando estado a: playing
2024-12-09 01:30:09.242 22605-22757 panjamon                com.wan.a2upra                       D  updateNotificationPlaybackState: playbackState actualizado a: playing
2024-12-09 01:30:09.242 22605-22757 panjamon                com.wan.a2upra                       D  showAudioNotification: Mostrando notificación - Título: Reproduciendo ahora, Autor: Artista, Estado: playing, audioSrc:
y luego aqui cuando doy pause no pasa nada (en la notificacion)
2024-12-09 01:30:15.975 22605-22605 panjamon                com.wan.a2upra                       D  NotificationActionReceiver: Acción recibida: TOGGLE_PLAY_PAUSE
2024-12-09 01:30:15.975 22605-22605 panjamon                com.wan.a2upra                       D  NotificationActionReceiver: Intentando toggle a través de MediaSession

la nofificacion se muestra sin los datos :(
 */

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private lateinit var userHelper: UserHelper
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var currentUrl: String? = null
    private lateinit var mediaSession: MediaSessionCompat

    private lateinit var notificationActionReceiver: NotificationActionReceiver

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permiso concedido, puedes mostrar notificaciones
            Toast.makeText(this, "Permiso para notificaciones concedido", Toast.LENGTH_SHORT).show()
        } else {
            // Permiso denegado
            Toast.makeText(this, "Permiso para notificaciones denegado", Toast.LENGTH_SHORT).show()
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback == null) return@registerForActivityResult

        val results = if (result.resultCode == RESULT_OK && result.data != null) {
            result.data?.data?.let { uri -> arrayOf(uri) }
        } else {
            null
        }

        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    class NotificationActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d("panjamon", "NotificationActionReceiver: Acción recibida: $action")

            if (action == "TOGGLE_PLAY_PAUSE") {
                Log.d("panjamon", "NotificationActionReceiver: Intentando toggle a través de MediaSession")

                // Accede a la MediaSession desde la MainActivity
                if (context is MainActivity) {
                    if (context.playbackState == "playing"){
                        context.mediaSession.controller.transportControls.pause()
                    } else {
                        context.mediaSession.controller.transportControls.play()
                    }
                }
            }
        }
    }

    private fun askNotificationPermission() {
        // Esto solo es necesario para Android 13+ (API nivel 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // Ya tienes el permiso, puedes mostrar notificaciones
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // Aquí puedes mostrar una explicación de por qué necesitas el permiso
                // antes de solicitarlo
                Toast.makeText(this, "Necesitamos permiso para mostrar notificaciones de reproducción", Toast.LENGTH_SHORT).show()

                // Solicitar el permiso
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Solicitar el permiso directamente
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermission()
        notificationActionReceiver = NotificationActionReceiver()
        val filter = IntentFilter("TOGGLE_PLAY_PAUSE")
        registerReceiver(notificationActionReceiver, filter, RECEIVER_NOT_EXPORTED)
        val targetUrl = intent.extras?.getString("target_url") ?: "https://2upra.com"
        userHelper = UserHelper(this)

        FirebaseApp.initializeApp(this)
        mediaSession = MediaSessionCompat(this, "AudioPlayerSession")

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                Log.d("panjamon", "MediaSession.Callback: onPlay")
                webView?.evaluateJavascript("playAudio()", null)
                this@MainActivity.playbackState = "playing"
                updateNotificationPlaybackState(this@MainActivity.playbackState, mediaSession)
            }

            override fun onPause() {
                Log.d("panjamon", "MediaSession.Callback: onPause")
                webView?.evaluateJavascript("pauseAudio()", null)
                this@MainActivity.playbackState = "paused"
                updateNotificationPlaybackState(this@MainActivity.playbackState, mediaSession)
            }

            override fun onStop() {
                Log.d("panjamon", "MediaSession.Callback: onStop")
            }
        })

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        window.statusBarColor = Color.parseColor("#050505")

        setContent {
            _2upraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebViewWithPullToRefresh(targetUrl, mediaSession)
                }
            }
        }

        // Registrar el BroadcastReceiver
        fun onDestroy() {
            super.onDestroy()
            mediaSession.release() // Libera la MediaSession cuando la actividad se destruya
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release() // Libera la MediaSession
        unregisterReceiver(notificationActionReceiver) // Desregistra el BroadcastReceiver
    }

    private fun evaluateUserIdFromWebView(webView: WebView) {
        webView.evaluateJavascript("window.userId") { userId ->
            val sanitizedUserId = userId?.replace("\"", "").takeIf { it != "null" }
            if (sanitizedUserId != null) {
                userHelper.saveUserId(sanitizedUserId)
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        userHelper.sendTokenToWordPress(token, sanitizedUserId)
                    }
                }
            }
        }
    }

    private fun handleDownload(url: String, userAgent: String, contentDisposition: String, mimetype: String, contentLength: Long) {


        val request = DownloadManager.Request(Uri.parse(url))

        // Obtener el nombre del archivo
        val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)

        // Configurar la solicitud
        request.setMimeType(mimetype)
        request.addRequestHeader("User-Agent", userAgent)
        request.setDescription("Descargando archivo...")
        request.setTitle(filename)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        // Limitar a Wi-Fi para evitar posibles reintentos por cambio de red
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)

        try {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        } catch (e: IllegalStateException) {
            Toast.makeText(this, "Error: No se pudo guardar el archivo.", Toast.LENGTH_LONG).show()
            return
        }

        // Obtener el DownloadManager y encolar la descarga
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager

        if (downloadManager == null) {
            Toast.makeText(this, "Error: No se pudo iniciar la descarga.", Toast.LENGTH_LONG).show()
            return
        }

        // Verificar si ya hay una descarga en curso con el mismo nombre de archivo
        val query = DownloadManager.Query()
        query.setFilterByStatus(DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_PAUSED)

        val cursor = downloadManager.query(query)
        var downloadAlreadyEnqueued = false
        while (cursor.moveToNext()) {
            val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (columnIndex != -1){
                val localUri = cursor.getString(columnIndex)
                if (localUri != null) {
                    val localFilename = Uri.parse(localUri).lastPathSegment
                    if (localFilename != null && localFilename.endsWith(filename)) {
                        downloadAlreadyEnqueued = true
                        break
                    }
                }
            }
        }
        cursor.close()

        if (downloadAlreadyEnqueued) {
            Toast.makeText(this, "Descarga con el mismo nombre ya iniciada o en espera...", Toast.LENGTH_LONG).show()
            return
        }

        try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            Toast.makeText(this, "Error al iniciar la descarga: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        // Mostrar un mensaje al usuario
        Toast.makeText(this, "Descarga iniciada...", Toast.LENGTH_LONG).show()
    }

    //no me deja hacer scroll, necesito hacer scroll
    @Composable
    fun WebViewWithPullToRefresh(url: String, mediaSession: MediaSessionCompat) {
        var isRefreshing by remember { mutableStateOf(false) }
        var webView by remember { mutableStateOf<WebView?>(null) }
        var swipeRefreshLayout by remember { mutableStateOf<SwipeRefreshLayout?>(null) }
        var shouldDisableSwipeRefresh by remember { mutableStateOf(false) }
        var startY by remember { mutableStateOf(0f) }
        var startX by remember { mutableStateOf(0f) }
        var isScrolling by remember { mutableStateOf(false) } // Nuevo: para detectar si ya estamos en un scroll

        AndroidView(factory = { context ->
            val newWebView = createConfiguredWebView(context) {
                isRefreshing = false
                webView?.let { evaluateUserIdFromWebView(it) }
            }.apply {
                loadUrl(url)
            }
            webView = newWebView

            SwipeRefreshLayout(context).also {
                swipeRefreshLayout = it
            }.apply {
                setOnRefreshListener {
                    webView?.reload()
                    isRefreshing = true
                }

                newWebView.setOnTouchListener { view, motionEvent ->
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startY = motionEvent.y
                            startX = motionEvent.x
                            isScrolling = false // Reiniciamos el estado de scroll
                            // Comprobar inicialmente si el toque es sobre un elemento 'no-refresh'
                            newWebView.evaluateJavascript(
                                "(function() { " +
                                        "  var element = document.elementFromPoint(${startX.toInt()}, ${startY.toInt()});" +
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
                            ) { result ->
                                shouldDisableSwipeRefresh = result == "true"
                            }
                            // Permitimos que el padre intercepte inicialmente
                            view.parent.requestDisallowInterceptTouchEvent(false)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val yDiff = motionEvent.y - startY
                            val xDiff = motionEvent.x - startX

                            if (Math.abs(yDiff) > Math.abs(xDiff)) { // Movimiento vertical
                                if (!isScrolling) {
                                    // Detectamos el inicio del scroll
                                    isScrolling = true

                                    if (yDiff > 0 && shouldDisableSwipeRefresh) { // Hacia abajo y sobre 'no-refresh'
                                        this@apply.isEnabled = false // Deshabilitamos SwipeRefreshLayout
                                        view.parent.requestDisallowInterceptTouchEvent(true)
                                    } else {
                                        this@apply.isEnabled = true
                                        view.parent.requestDisallowInterceptTouchEvent(false)
                                    }
                                } else if (shouldDisableSwipeRefresh) {
                                    // Si ya se ha iniciado un scroll y el elemento es 'no-refresh', mantener la configuración
                                    if (yDiff > 0){
                                        view.parent.requestDisallowInterceptTouchEvent(true)
                                    } else {
                                        view.parent.requestDisallowInterceptTouchEvent(false)
                                    }
                                }
                            } else { // Movimiento horizontal
                                isScrolling = true
                                view.parent.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            this@apply.isEnabled = true
                            shouldDisableSwipeRefresh = false
                            isScrolling = false // Reseteamos el estado de scroll
                            view.parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false
                }
                addView(newWebView)
            }
        }, update = {
            it.isRefreshing = isRefreshing
        })
    }

    fun createConfiguredWebView(context: Context, onPageFinished: () -> Unit): WebView {
        return WebView(context).apply {
            // Configuración de cookies
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            addJavascriptInterface(AudioPlayerInterface(context), "Android")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d("panjamon", "WebViewClient: onPageFinished: $url")
                    super.onPageFinished(view, url)

                    // Inyecta el script para verificar si la interfaz 'Android' está disponible
                    view?.evaluateJavascript("""
        console.log = function(message) {
            if (typeof Android !== 'undefined') {
                Android.log('panjamon: Console.log: ' + message);
            }
        };
        if (typeof Android !== 'undefined') {
            Android.log('panjamon: Interfaz Android detectada');
            if (typeof window.updateNotificationPlaybackState === 'function') {
                Android.log('panjamon: Función updateNotificationPlaybackState encontrada');
            } else {
                Android.log('panjamon: Función updateNotificationPlaybackState NO encontrada');
            }
        } else {
            Android.log('panjamon: Interfaz Android NO detectada');
        }
    """, null)

                    onPageFinished()
                    evaluateUserIdFromWebView(view!!) // Asegúrate de que view no es null
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val allowedDomains = listOf("accounts.google.com", "google.com", "2upra.com", "checkout.stripe.com", ".stripe.com")
                    val url = request.url.toString()
                    // Permitir URLs de dominios autorizados
                    if (allowedDomains.any { url.contains(it) }) {
                        return false
                    }
                    // Bloquear otras URLs
                    return true
                }
            }

            settings.apply {
                javaScriptEnabled = true
                allowFileAccess = true
                domStorageEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)

                // Establecer el User-Agent personalizado
                userAgentString = "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Mobile Safari/537.36 AppAndroid"
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    // Manejo de selección de archivos
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback

                    val intent = fileChooserParams?.createIntent()
                    return if (intent != null) {
                        try {
                            fileChooserLauncher.launch(intent)
                            true
                        } catch (e: Exception) {
                            this@MainActivity.filePathCallback = null
                            false
                        }
                    } else {
                        this@MainActivity.filePathCallback = null
                        false
                    }
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                // Verifica y solicita permisos usando this@MainActivity
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    handleDownload(url, userAgent, contentDisposition, mimetype, contentLength)
                } else {
                    // Solicita permisos usando this@MainActivity
                    this@MainActivity.requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
            setBackgroundColor(Color.parseColor("#050505"))
        }
    }

    class AudioPlayerInterface(private val context: Context) {
        @JavascriptInterface
        fun sendAudioInfo(title: String, author: String, imageUrl: String, audioSrc: String) {
            Log.d("panjamon", "AudioPlayerInterface.sendAudioInfo: Título: $title, Autor: $author, Imagen: $imageUrl, AudioSrc: $audioSrc")
            (context as MainActivity).showAudioNotification(title, author, imageUrl, audioSrc, (context as MainActivity).mediaSession)
        }

        @JavascriptInterface
        fun updateNotificationPlaybackState(state: String) {
            Log.d("panjamon", "AudioPlayerInterface.updateNotificationPlaybackState: Estado recibido de WebView: $state")
            (context as MainActivity).updateNotificationPlaybackState(state, (context as MainActivity).mediaSession)
        }

        @JavascriptInterface
        fun log(message: String) {
            Log.d("panjamon", "AudioPlayerInterface: $message")
        }
    }

    private val notificationId = 1
    private var playbackState = "paused"

    fun showAudioNotification(title: String, author: String, imageUrl: String, audioSrc: String, mediaSession: MediaSessionCompat) {
        Log.d("panjamon", "showAudioNotification: Mostrando notificación - Título: $title, Autor: $author, Estado: $playbackState, audioSrc: $audioSrc")

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
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


        val largeIcon = if (imageUrl.isNotEmpty()) {
            try {
                val bitmap = Glide.with(this)
                    .asBitmap()
                    .load(imageUrl)
                    .override(128, 128) // Redimensiona la imagen a 128x128
                    .submit()
                    .get()
                bitmap
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(author)
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(largeIcon)
            .addAction(playPauseAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0) // Muestra las acciones en la vista compacta
                    .setMediaSession(mediaSession.sessionToken) // <-- Agrega esta línea
            )
            .setOngoing(playbackState == "playing")
            .build()

        notificationManager.notify(notificationId, notification)

    }

    fun updateNotificationPlaybackState(state: String, mediaSession: MediaSessionCompat) {
        Log.d("panjamon", "updateNotificationPlaybackState: Actualizando estado a: $state")
        playbackState = state
        Log.d("panjamon", "updateNotificationPlaybackState: playbackState actualizado a: $playbackState")
        showAudioNotification(
            "Reproduciendo ahora", // Título temporal, usa el actual si lo guardas en una variable global
            "Artista", // Autor temporal
            "", // URL de imagen temporal
            "", // Fuente de audio temporal
            mediaSession

        )

        // Actualiza el estado de la MediaSession
        updateMediaSessionState(state, mediaSession)
    }


    private fun updateMediaSessionState(state: String, mediaSession: MediaSessionCompat) {
        val playbackStateBuilder = PlaybackStateCompat.Builder()

        if (state == "playing") {
            playbackStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
            playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP)
        } else {
            playbackStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, 0, 1f)
            playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY)
        }

        mediaSession.setPlaybackState(playbackStateBuilder.build())
    }


    // Pendiente para manejar acciones como play/pause desde la notificación
    private fun getPendingIntentForAction(action: String): PendingIntent {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }


}

class UserHelper(private val context: Context) {

    fun sendTokenToWordPress(token: String, userId: String) {
        val url = "https://2upra.com/wp-json/custom/v1/save-token"
        val requestQueue = Volley.newRequestQueue(context)

        // Obtenemos la versión actual de la app
        val appVersionName = getAppVersionName()
        val appVersionCode = getAppVersionCode()

        // Creamos el JSON que se enviará al servidor
        val jsonObject = JSONObject().apply {
            put("token", token)
            put("userId", userId)
            put("appVersionName", appVersionName) // Ejemplo: "1.0.2"
            put("appVersionCode", appVersionCode) // Ejemplo: 3
        }

        val jsonObjectRequest = object : JsonObjectRequest(
            Method.POST, url, jsonObject,
            { response ->
                // Manejar respuesta exitosa del servidor (opcional)
                Log.d("UserHelper", "Token enviado con éxito: $response")
            },
            { error ->
                // Manejar error de la solicitud
                Log.e("UserHelper", "Error enviando el token: ${error.message}")
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf("Content-Type" to "application/json")
            }
        }

        requestQueue.add(jsonObjectRequest)
    }

    fun saveUserId(userId: String) {
        context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit().apply {
            putString("userId", userId)
            apply()
        }
    }

    // Función para obtener el nombre de la versión (ejemplo: "1.0.2")
    private fun getAppVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    // Función para obtener el código de la versión (ejemplo: 3)
    private fun getAppVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionCode
        } catch (e: Exception) {
            -1 // Retornar -1 en caso de error
        }
    }
}

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val targetUrl = remoteMessage.data["url"]
        showNotification(remoteMessage.notification?.title, remoteMessage.notification?.body, targetUrl)
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
            .setSmallIcon(R.mipmap.a2upra)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(0, notification)
    }
}


