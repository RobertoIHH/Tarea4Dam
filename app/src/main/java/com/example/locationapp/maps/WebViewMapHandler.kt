package com.example.locationapp.maps

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.locationapp.data.PointOfInterest
import com.example.locationapp.data.ExploredZone
import org.json.JSONArray
import org.json.JSONObject

/**
 * Clase para manejar la visualización de mapas usando WebView con Leaflet.js
 */
class WebViewMapHandler(private val context: Context) {

    // Interfaz JavaScript para la comunicación WebView <-> Kotlin
    class WebAppInterface(
        private val context: Context,
        private val onLocationLongPress: (Double, Double) -> Unit,
        private val onPointOfInterestClick: (Long) -> Unit
    ) {
        @JavascriptInterface
        fun showToast(message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        @JavascriptInterface
        fun onMapLongPress(latitude: Double, longitude: Double) {
            onLocationLongPress(latitude, longitude)
        }

        @JavascriptInterface
        fun onPointOfInterestClicked(poiId: Long) {
            onPointOfInterestClick(poiId)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun LeafletMap(
        modifier: Modifier = Modifier,
        currentLatitude: Double,
        currentLongitude: Double,
        pointsOfInterest: List<PointOfInterest>,
        exploredZones: List<ExploredZone>,
        routeJsonArray: String = "[]",
        onLocationLongPress: (Double, Double) -> Unit,
        onPointOfInterestClick: (Long) -> Unit
    ) {
        // Implementación básica
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setGeolocationEnabled(true)

                    webViewClient = WebViewClient()

                    loadUrl("https://www.openstreetmap.org/#map=15/$currentLatitude/$currentLongitude")
                }
            }
        )
    }
}