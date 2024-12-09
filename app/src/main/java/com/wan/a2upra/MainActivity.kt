package com.wan.a2upra

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
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
import android.content.IntentFilter
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat.startActivityForResult
import com.bumptech.glide.Glide
import android.os.Build

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private lateinit var userHelper: UserHelper

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var currentUrl: String? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback == null) return@registerForActivityResult

        val results = if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            result.data?.data?.let { uri -> arrayOf(uri) }
        } else {
            null
        }

        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    private val notificationActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == "TOGGLE_PLAY_PAUSE") {
                webView?.evaluateJavascript("togglePlayPause()", null)
                // Aquí, también necesitas actualizar el estado de reproducción y la notificación misma.
                // Puedes usar una variable global o SharedPreferences para manejar el estado.
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetUrl = intent.extras?.getString("target_url") ?: "https://2upra.com"
        userHelper = UserHelper(this)

        FirebaseApp.initializeApp(this)

        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        window.statusBarColor = android.graphics.Color.parseColor("#050505")

        setContent {
            _2upraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebViewWithPullToRefresh(targetUrl)
                }
            }
        }

        // Registrar el BroadcastReceiver
        // registerReceiver(notificationActionReceiver, IntentFilter("TOGGLE_PLAY_PAUSE"))
    }

    override fun onDestroy() {
        super.onDestroy()
        // Desregistrar el BroadcastReceiver
        unregisterReceiver(notificationActionReceiver)
    }

    @Composable
    fun WebViewWithPullToRefresh(url: String) {
        var isRefreshing by remember { mutableStateOf(false) }

        AndroidView(factory = { context ->
            // Inicializa webView aquí
            val newWebView = createConfiguredWebView(context) {
                isRefreshing = false
                webView?.let { evaluateUserIdFromWebView(it) }
            }.apply {
                // Carga la URL después de la inicialización
                loadUrl(url)
            }
            webView = newWebView // Asigna la nueva instancia a la propiedad webView

            // Luego crea y configura SwipeRefreshLayout
            SwipeRefreshLayout(context).apply {
                setOnRefreshListener {
                    webView?.reload() // Usa el operador de llamada segura ?.
                    isRefreshing = true
                }
                addView(newWebView) // Agrega newWebView en lugar de webView
            }
        }, update = {
            it.isRefreshing = isRefreshing
        })
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


    fun createConfiguredWebView(context: Context, onPageFinished: () -> Unit): WebView {
        return WebView(context).apply {
            // Configuración de cookies
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            addJavascriptInterface(AudioPlayerInterface(context), "Android")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    onPageFinished()
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val allowedDomains = listOf("accounts.google.com", "google.com", "2upra.com")
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

            setBackgroundColor(android.graphics.Color.parseColor("#050505"))
        }
    }



    class AudioPlayerInterface(private val context: Context) {

        // Mostrar notificación cuando se inicie la reproducción
        @JavascriptInterface
        fun sendAudioInfo(title: String, author: String, imageUrl: String, audioSrc: String) {
            (context as MainActivity).showAudioNotification(title, author, imageUrl, audioSrc)
        }

        // Actualizar el estado de la reproducción (play/pause)
        @JavascriptInterface
        fun updatePlaybackState(state: String) {
            (context as MainActivity).updateNotificationPlaybackState(state)
        }
    }

    private val notificationId = 1
    private var playbackState = "paused"

    fun showAudioNotification(title: String, author: String, imageUrl: String, audioSrc: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "audio_playback_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Reproducción de audio",
                NotificationManager.IMPORTANCE_LOW
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
                Glide.with(this).asBitmap().load(imageUrl).submit().get()
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
                    .setShowActionsInCompactView(0)
            )
            .setOngoing(playbackState == "playing")
            .build()

        notificationManager.notify(notificationId, notification)
    }


    fun updateNotificationPlaybackState(state: String) {
        playbackState = state
        // Actualizar la notificación existente
        showAudioNotification(
            "Reproduciendo ahora", // Título temporal, usa el actual si lo guardas en una variable global
            "Artista", // Autor temporal
            "", // URL de imagen temporal
            "" // Fuente de audio temporal
        )
    }

    // Pendiente para manejar acciones como play/pause desde la notificación
    private fun getPendingIntentForAction(action: String): PendingIntent {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    class NotificationActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val mainActivity = context as MainActivity

            if (action == "TOGGLE_PLAY_PAUSE") {
                mainActivity.webView?.evaluateJavascript("togglePlayPause()", null)
            }
        }
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
            Request.Method.POST, url, jsonObject,
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
        if (targetUrl != null) {
            Log.d("panjamon", "URL recibida en el mensaje: $targetUrl")
        } else {
            Log.e("panjamon", "No se recibió URL en el mensaje")
        }

        showNotification(remoteMessage.notification?.title, remoteMessage.notification?.body, targetUrl)
    }

    private fun showNotification(title: String?, body: String?, targetUrl: String?) {
        val channelId = "default_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
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


