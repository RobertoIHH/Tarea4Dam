package com.example.locationapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class GoogleMapsActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var loadStartTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_google_maps)

        // Inicializar vistas
        webView = findViewById(R.id.webViewGoogle)
        progressBar = findViewById(R.id.progressBarGoogle)

        // Inicializar el proveedor de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Configurar el WebView
        setupWebView()

        // Verificar permisos y obtener ubicación
        if (checkLocationPermissions()) {
            getCurrentLocation()
        } else {
            Toast.makeText(this, "Se requieren permisos de ubicación", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE

                // Calcular tiempo de carga para métricas
                val loadEndTime = System.currentTimeMillis()
                val loadTime = loadEndTime - loadStartTime
                println("Tiempo de carga de Google Maps: $loadTime ms")
            }
        }
    }

    private fun checkLocationPermissions(): Boolean {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        )

        return fineLocationPermission == PackageManager.PERMISSION_GRANTED &&
                coarseLocationPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun getCurrentLocation() {
        progressBar.visibility = View.VISIBLE
        loadStartTime = System.currentTimeMillis()

        try {
            val cancellationToken = CancellationTokenSource()

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    loadGoogleMaps(location.latitude, location.longitude)
                } else {
                    handleLocationError("No se pudo obtener la ubicación")
                }
            }.addOnFailureListener { e ->
                handleLocationError("Error al obtener la ubicación: ${e.message}")
            }
        } catch (e: SecurityException) {
            handleLocationError("Se requieren permisos de ubicación")
        }
    }

    private fun loadGoogleMaps(latitude: Double, longitude: Double) {
        // Cargar Google Maps con la ubicación actual
        val googleMapsUrl = "https://www.google.com/maps/@$latitude,$longitude,15z"
        webView.loadUrl(googleMapsUrl)
    }

    private fun handleLocationError(message: String) {
        progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // Cargar un mapa genérico en caso de error
        loadGoogleMaps(40.416775, -3.703790) // Madrid como ubicación por defecto
    }
}