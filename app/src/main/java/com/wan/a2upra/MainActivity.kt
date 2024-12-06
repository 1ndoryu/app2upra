package com.wan.a2upra

import android.annotation.SuppressLint
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
import android.content.Intent


class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.extras?.let { extras ->
            val targetUrl = extras.getString("target_url")
            if (targetUrl != null) {
                // Cargar la URL en la WebView
                webView.loadUrl(targetUrl)
            }
        }

        // Inicializar Firebase
        FirebaseApp.initializeApp(this)

        // Configuración de la interfaz de usuario
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        window.statusBarColor = Color.parseColor("#050505")

        setContent {
            _2upraTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WebViewWithPullToRefresh("https://2upra.com")
                }
            }
        }
    }

    /**
     * Función para enviar el token de Firebase al servidor WordPress
     */
    private fun sendTokenToWordPress(token: String, userId: String) {
        val url = "https://2upra.com/wp-json/custom/v1/save-token"
        val requestQueue = Volley.newRequestQueue(this)

        val jsonObject = JSONObject().apply {
            put("token", token)
            put("userId", userId)
        }

        val jsonObjectRequest = object : JsonObjectRequest(
            Request.Method.POST, url, jsonObject,
            { response ->
                Log.d("panjamon", "Token enviado correctamente a WordPress. Respuesta: $response")
            },
            { error ->
                Log.e("panjamon", "Error enviando el token: ${error.message}")
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Content-Type"] = "application/json"
                return headers
            }
        }

        requestQueue.add(jsonObjectRequest)
    }

    /**
     * Función para guardar el userId en SharedPreferences
     */
    private fun saveUserId(userId: String) {
        val sharedPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString("userId", userId)
            apply()
        }
        Log.d("panjamon", "UserId guardado en SharedPreferences: $userId")
    }

    /**
     * Composable para mostrar una WebView con funcionalidad de pull-to-refresh
     */
    @Composable
    fun WebViewWithPullToRefresh(url: String) {
        var isRefreshing by remember { mutableStateOf(false) }

        AndroidView(factory = { context ->
            val swipeRefreshLayout = SwipeRefreshLayout(context).apply {
                setOnRefreshListener {
                    webView.reload()
                }
            }

            webView = WebView(context).apply {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isRefreshing = false
                        swipeRefreshLayout.isRefreshing = false
                        Log.d("panjamon", "Página terminada de cargar: $url")

                        // Ejecutar JavaScript para obtener userId
                        evaluateUserIdFromWebView()
                    }
                }

                settings.javaScriptEnabled = true
                loadUrl(url)
                setBackgroundColor(Color.parseColor("#050505"))
            }

            swipeRefreshLayout.addView(webView)
            swipeRefreshLayout
        }, update = { swipeRefreshLayout ->
            swipeRefreshLayout.isRefreshing = isRefreshing
        })
    }

    /**
     * Función para ejecutar JavaScript en la WebView y obtener el userId
     */
    private fun evaluateUserIdFromWebView() {
        webView.evaluateJavascript("window.userId") { userId ->
            if (userId != null && userId != "null") {
                val sanitizedUserId = userId.replace("\"", "") // Elimina comillas del resultado
                Log.d("panjamon", "UserId obtenido de la WebView: $sanitizedUserId")
                saveUserId(sanitizedUserId)

                // Obtener el token de Firebase y enviarlo al servidor
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        sendTokenToWordPress(token, sanitizedUserId)
                    } else {
                        Log.e("panjamon", "Error al obtener el token de Firebase: ${task.exception?.message}")
                    }
                }
            } else {
                Log.e("panjamon", "No se pudo obtener el UserId desde la WebView")
            }
        }
    }
}

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.notification?.let {
            // Extraer datos adicionales del mensaje, si existen
            val data = remoteMessage.data
            val targetUrl = data["url"] // Suponiendo que el mensaje incluye un campo "url"
            val userId = data["userId"] // Si necesitas otro dato, como el ID del usuario

            // Mostrar la notificación con la acción deseada
            showNotification(it.title, it.body, targetUrl)
        }
    }

    private fun showNotification(title: String?, body: String?, targetUrl: String?) {
        val channelId = "default_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Crear el canal de notificación si es necesario (para Android 8.0+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Crear un Intent para abrir la actividad deseada
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            // Pasar datos adicionales a través del Intent
            targetUrl?.let {
                putExtra("target_url", it) // Agregar la URL al Intent
            }
        }

        // Crear el PendingIntent asociado al Intent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Construir la notificación
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.mipmap.a2upra)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Mostrar la notificación
        notificationManager.notify(0, notification)
    }
}


