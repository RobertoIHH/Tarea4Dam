package com.example.locationapp

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Star // Usaremos Bookmark en lugar de Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.locationapp.ui.theme.LocationAppTheme
import com.example.locationapp.viewmodel.LocationViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var viewModel: LocationViewModel
    private var webViewReference: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate iniciado")

        // Inicializar el cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        Log.d("MainActivity", "FusedLocationClient inicializado")

        // Inicializar ViewModel
        viewModel = ViewModelProvider(this).get(LocationViewModel::class.java)
        Log.d("MainActivity", "ViewModel inicializado")

        setContent {
            Log.d("MainActivity", "Configurando UI con Compose")
            LocationAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var selectedTabIndex by remember { mutableStateOf(0) }

                    Column {
                        TabRow(selectedTabIndex = selectedTabIndex) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                text = { Text("Mapa") }
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                text = { Text("Explorar") }
                            )
                            Tab(
                                selected = selectedTabIndex == 2,
                                onClick = { selectedTabIndex = 2 },
                                text = { Text("Perfil") }
                            )
                        }

                        when (selectedTabIndex) {
                            0 -> {
                                MapScreen()
                            }
                            1 -> {
                                ExploreScreen()
                            }
                            2 -> {
                                ProfileScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun MapScreen() {
        val context = LocalContext.current
        val webViewRef = remember { mutableStateOf<WebView?>(null) }
        val isLoading = remember { mutableStateOf(true) }
        val searchQuery = remember { mutableStateOf("") }
        val coroutineScope = rememberCoroutineScope()
        val sharedPreferences = remember { context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE) }

        // Solicitud de permisos de ubicación
        val locationPermissionRequest = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (locationGranted) {
                // Ubicación concedida, centrar mapa
                getCurrentLocation(webViewRef.value)
            } else {
                Toast.makeText(context, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
            }
        }

        // HTML local para el mapa
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Mapa OpenStreetMap</title>
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    body, html, #map {
                        height: 100%;
                        margin: 0;
                        padding: 0;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map').setView([40.416775, -3.703790], 13);
                    
                    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 19,
                        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                    }).addTo(map);
                    
                    // Variable para almacenar marcadores
                    var markers = [];
                    
                    // Función para agregar marcador
                    function addMarker(lat, lng, title) {
                        var marker = L.marker([lat, lng]).addTo(map);
                        if (title) {
                            marker.bindPopup("<b>" + title + "</b><br>Latitud: " + lat + "<br>Longitud: " + lng);
                        }
                        markers.push(marker);
                        return markers.length - 1;
                    }
                    
                    // Función para centrar el mapa
                    function centerMap(lat, lng, zoom) {
                        map.setView([lat, lng], zoom || 15);
                    }
                    
                    // Búsqueda de lugares
                    function searchLocation(query) {
                        fetch('https://nominatim.openstreetmap.org/search?format=json&q=' + encodeURIComponent(query))
                            .then(response => response.json())
                            .then(data => {
                                if (data.length > 0) {
                                    var result = data[0];
                                    centerMap(result.lat, result.lon);
                                    addMarker(result.lat, result.lon, result.display_name);
                                }
                                return true;
                            })
                            .catch(() => {
                                return false;
                            });
                    }
                    
                    // Función para eliminar todos los marcadores
                    function clearMarkers() {
                        for (var i = 0; i < markers.length; i++) {
                            map.removeLayer(markers[i]);
                        }
                        markers = [];
                    }
                    
                    // Función para obtener centro del mapa
                    function getMapCenter() {
                        var center = map.getCenter();
                        return JSON.stringify({lat: center.lat, lng: center.lng, zoom: map.getZoom()});
                    }
                    
                    // Inicializar mapa
                    document.addEventListener('DOMContentLoaded', function() {
                        setTimeout(function() {
                            // Notificar a Android que el mapa está listo
                            if (window.Android) {
                                window.Android.onMapReady();
                            }
                        }, 500);
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        Box(modifier = Modifier.fillMaxSize()) {
            // WebView para mostrar el mapa
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setGeolocationEnabled(true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading.value = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading.value = false

                                // Cargar ubicación guardada si existe
                                val savedLocation = sharedPreferences.getString("last_location", null)
                                if (savedLocation != null) {
                                    view?.evaluateJavascript(
                                        "try { " +
                                                "var loc = $savedLocation; " +
                                                "centerMap(loc.lat, loc.lng, loc.zoom || 15); " +
                                                "} catch(e) { console.error(e); }",
                                        null
                                    )
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                Toast.makeText(
                                    context,
                                    "Error al cargar el mapa: ${error?.description}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                isLoading.value = false
                            }
                        }

                        // Cargar contenido HTML para el mapa
                        loadDataWithBaseURL(
                            "https://openstreetmap.org",
                            htmlContent,
                            "text/html",
                            "UTF-8",
                            null
                        )

                        webViewRef.value = this
                        webViewReference = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Barra de búsqueda
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery.value,
                    onValueChange = { searchQuery.value = it },
                    placeholder = { Text("Buscar lugares...") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.value.isNotEmpty()) {
                                webViewRef.value?.evaluateJavascript(
                                    "searchLocation('${searchQuery.value.replace("'", "\\'")}');",
                                    null
                                )
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (searchQuery.value.isNotEmpty()) {
                                webViewRef.value?.evaluateJavascript(
                                    "searchLocation('${searchQuery.value.replace("'", "\\'")}');",
                                    null
                                )
                            }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                    }
                )
            }

            // Botones flotantes
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón de ubicación actual
                FloatingActionButton(
                    onClick = {
                        requestLocationPermission(locationPermissionRequest)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Mi ubicación"
                    )
                }

                // Botón para guardar ubicación
                FloatingActionButton(
                    onClick = {
                        saveCurrentMapView(webViewRef.value, sharedPreferences)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bookmarks,
                        contentDescription = "Guardar ubicación"
                    )
                }

                // Botón para limpiar marcadores
                FloatingActionButton(
                    onClick = {
                        webViewRef.value?.evaluateJavascript("clearMarkers();", null)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Limpiar marcadores"
                    )
                }

                // Botón principal
                FloatingActionButton(
                    onClick = {
                        // Agregar marcador en el centro del mapa
                        webViewRef.value?.evaluateJavascript(
                            "(function() { " +
                                    "var center = map.getCenter(); " +
                                    "addMarker(center.lat, center.lng, 'Marcador'); " +
                                    "})();",
                            null
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir marcador")
                }
            }

            // Indicador de carga
            if (isLoading.value) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    private fun saveCurrentMapView(webView: WebView?, sharedPreferences: SharedPreferences) {
        webView?.evaluateJavascript("getMapCenter();") { result ->
            // Eliminar comillas del resultado
            val locationJson = result.trim('"').replace("\\\"", "\"").replace("\\\\", "\\")
            try {
                // Guardar ubicación actual
                val editor = sharedPreferences.edit()
                editor.putString("last_location", locationJson)
                editor.apply()

                Toast.makeText(this, "Ubicación guardada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error al guardar ubicación", Toast.LENGTH_SHORT).show()
                Log.e("MapScreen", "Error: ${e.message}")
            }
        }
    }

    private fun requestLocationPermission(permissionLauncher: ActivityResultLauncher<Array<String>>) {
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCoarseLocationPermission || hasFineLocationPermission) {
            // Ya tenemos el permiso, obtenemos la ubicación
            getCurrentLocation(webViewReference)
        } else {
            // Solicitar permisos
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun getCurrentLocation(webView: WebView?) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        // Centrar mapa en la ubicación actual
                        webView?.evaluateJavascript(
                            "centerMap(${location.latitude}, ${location.longitude});",
                            null
                        )
                    } else {
                        Toast.makeText(
                            this,
                            "No se pudo obtener la ubicación actual",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        "Error al obtener ubicación: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    @Composable
    fun ExploreScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "Explorar la ciudad",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { 0.35f },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Has explorado el 35% de las zonas disponibles",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Rutas Sugeridas",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Ruta de ejemplo
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Ruta de Monumentos",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Visita los principales monumentos de la ciudad",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            Toast.makeText(
                                this@MainActivity,
                                "Funcionalidad de visualización de rutas en desarrollo",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Ver en mapa")
                    }
                }
            }
        }
    }

    @Composable
    fun ProfileScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Perfil de Usuario",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "U",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Usuario Explorador",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                "usuario@ejemplo.com",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Preferencias de usuario
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Preferencias",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = true,
                            onCheckedChange = { /* TODO */ }
                        )
                        Text("Notificaciones")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = false,
                            onCheckedChange = { /* TODO */ }
                        )
                        Text("Modo oscuro")
                    }

                    Button(
                        onClick = {
                            Toast.makeText(
                                this@MainActivity,
                                "Configuración guardada",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 16.dp)
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}
