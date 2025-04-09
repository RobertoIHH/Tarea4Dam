package com.example.locationapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.locationapp.ui.theme.LocationAppTheme
import com.example.locationapp.viewmodel.LocationViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var viewModel: LocationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar el proveedor de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Inicializar ViewModel
        viewModel = ViewModelProvider(this)[LocationViewModel::class.java]

        setContent {
            LocationAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }

    @Composable
    fun MainScreen(viewModel: LocationViewModel = viewModel()) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        var currentTab by remember { mutableStateOf(0) }
        var currentLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
        var loading by remember { mutableStateOf(true) }
        var progress by remember { mutableStateOf(0f) }

        // Solicitud de permisos
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val locationGranted = permissions.entries.all { it.value }
            if (locationGranted) {
                getCurrentLocation()
            } else {
                Toast.makeText(context, "Se requieren permisos de ubicación para funcionar correctamente", Toast.LENGTH_LONG).show()
            }
        }

        // Verificar permisos al inicio
        LaunchedEffect(key1 = true) {
            checkLocationPermissions(requestPermissionLauncher)
        }

        // Observar el progreso de exploración
        LaunchedEffect(viewModel) {
            viewModel.explorationProgress.observe(context as ComponentActivity) { newProgress ->
                progress = newProgress
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Tabs para navegación
            TabRow(selectedTabIndex = currentTab) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    text = { Text("OpenStreetMap") }
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    text = { Text("Google Maps") }
                )
                Tab(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    text = { Text("Exploración") }
                )
            }

            // Contenido según el tab seleccionado
            when (currentTab) {
                0 -> {
                    // OpenStreetMap
                    Box(modifier = Modifier.fillMaxSize()) {
                        // WebView para OpenStreetMap
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                }
                            },
                            update = { webView ->
                                currentLocation?.let { (latitude, longitude) ->
                                    loadOpenStreetMap(webView, latitude, longitude)
                                    loading = false
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Loading indicator
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        // Botón para añadir POI
                        FloatingActionButton(
                            onClick = {
                                currentLocation?.let { location ->
                                    coroutineScope.launch {
                                        // Mostrar diálogo para añadir POI
                                        showAddPoiDialog(location.first, location.second)
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Text("+")
                        }

                        // Barra de progreso de exploración
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .fillMaxWidth(0.8f)
                        )
                    }
                }
                1 -> {
                    // Google Maps
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                }
                            },
                            update = { webView ->
                                currentLocation?.let { (latitude, longitude) ->
                                    loadGoogleMaps(webView, latitude, longitude)
                                    loading = false
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
                2 -> {
                    // Pantalla de Exploración
                    ExplorationScreen(viewModel)
                }
            }
        }
    }

    private fun checkLocationPermissions(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (fineLocationPermission == PackageManager.PERMISSION_GRANTED &&
            coarseLocationPermission == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun getCurrentLocation() {
        try {
            val cancellationToken = CancellationTokenSource()

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    // Actualizar ubicación en el ViewModel
                    viewModel.setCurrentLocation(location.latitude, location.longitude)
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

    private fun loadOpenStreetMap(webView: WebView, latitude: Double, longitude: Double) {
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        // Crear HTML con Leaflet para mostrar el mapa
        val leafletHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    body { margin: 0; padding: 0; }
                    html, body, #map { height: 100%; width: 100%; }
                    .progress-container {
                        position: absolute;
                        bottom: 20px;
                        left: 50%;
                        transform: translateX(-50%);
                        background: rgba(255,255,255,0.8);
                        padding: 10px;
                        border-radius: 5px;
                        box-shadow: 0 0 10px rgba(0,0,0,0.2);
                        z-index: 1000;
                    }
                    .poi-marker {
                        border-radius: 50%;
                        width: 12px;
                        height: 12px;
                        border: 2px solid white;
                    }
                    .poi-marker.visited { background-color: #4CAF50; }
                    .poi-marker.unvisited { background-color: #2196F3; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    // Inicializar el mapa centrado en la ubicación actual
                    var map = L.map('map').setView([$latitude, $longitude], 15);
                    
                    // Añadir capa de mapa de OpenStreetMap
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                    }).addTo(map);
                    
                    // Añadir marcador en la posición actual
                    var marker = L.marker([$latitude, $longitude]).addTo(map);
                    marker.bindPopup("<b>Mi ubicación</b><br>Actualizado: $currentTime").openPopup();
                    
                    // Añadir un círculo para mostrar precisión aproximada
                    var circle = L.circle([$latitude, $longitude], {
                        color: 'blue',
                        fillColor: '#3388ff',
                        fillOpacity: 0.1,
                        radius: 100
                    }).addTo(map);
                    
                    // Variables para almacenar capas
                    var poisLayer = L.layerGroup().addTo(map);
                    var zonesLayer = L.layerGroup().addTo(map);
                    
                    // Función para actualizar puntos de interés
                    function updatePOIs(poisData) {
                        // Limpiar capa de POIs
                        poisLayer.clearLayers();
                        
                        // Parsear datos JSON
                        const pois = JSON.parse(poisData);
                        
                        // Añadir cada POI al mapa
                        pois.forEach(poi => {
                            const markerColor = poi.visited ? 'visited' : 'unvisited';
                            
                            // Crear marcador personalizado
                            const poiMarker = L.divIcon({
                                className: `poi-marker ${markerColor}`,
                                html: '',
                                iconSize: [16, 16]
                            });
                            
                            // Añadir marcador al mapa
                            const marker = L.marker([poi.lat, poi.lng], {
                                icon: poiMarker,
                                title: poi.name
                            }).addTo(poisLayer);
                            
                            // Añadir popup con información
                            marker.bindPopup(
                                `<b>${poi.name}</b><br>` +
                                `Categoría: ${poi.category}<br>` +
                                `Estado: ${poi.visited ? 'Visitado' : 'Por visitar'}<br>` +
                                `<button onclick="AndroidInterface.markPoiAsVisited(${poi.id})">Marcar como visitado</button>`
                            );
                            
                            // Añadir evento de clic
                            marker.on('click', function() {
                                AndroidInterface.onPoiClick(poi.id);
                            });
                        });
                    }
                    
                    // Función para actualizar zonas
                    function updateZones(zonesData) {
                        // Limpiar capa de zonas
                        zonesLayer.clearLayers();
                        
                        // Parsear datos JSON
                        const zones = JSON.parse(zonesData);
                        
                        // Añadir cada zona al mapa
                        zones.forEach(zone => {
                            // Crear polígono para la zona
                            const polygon = L.polygon(zone.coordinates, {
                                color: zone.discovered ? '#4CAF50' : '#FF9800',
                                fillColor: zone.discovered ? '#A5D6A7' : '#FFE0B2',
                                fillOpacity: 0.3,
                                weight: 2
                            }).addTo(zonesLayer);
                            
                            // Añadir popup con información
                            polygon.bindPopup(
                                `<b>${zone.name}</b><br>` +
                                `Estado: ${zone.discovered ? 'Descubierta' : 'Por descubrir'}`
                            );
                        });
                    }
                    
                    // Función para actualizar progreso de exploración
                    function updateExplorationProgress(percent) {
                        // Si no existe el contenedor de progreso, crearlo
                        let progressContainer = document.querySelector('.progress-container');
                        if (!progressContainer) {
                            progressContainer = document.createElement('div');
                            progressContainer.className = 'progress-container';
                            document.body.appendChild(progressContainer);
                        }
                        
                        // Actualizar contenido del contenedor
                        progressContainer.innerHTML = `
                            <div>Exploración: ${percent}%</div>
                            <div style="background: #eee; height: 10px; width: 200px; margin-top: 5px;">
                                <div style="background: #4CAF50; height: 10px; width: ${percent * 2}px;"></div>
                            </div>
                        `;
                    }
                    
                    // Manejar clic en el mapa para añadir puntos
                    map.on('contextmenu', function(e) {
                        AndroidInterface.onMapClick(e.latlng.lat, e.latlng.lng);
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(
            "https://openstreetmap.org",
            leafletHtml,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun loadGoogleMaps(webView: WebView, latitude: Double, longitude: Double) {
        // Cargar Google Maps con la ubicación actual
        val googleMapsUrl = "https://www.google.com/maps/@$latitude,$longitude,15z"
        webView.loadUrl(googleMapsUrl)
    }

    private fun handleLocationError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showAddPoiDialog(latitude: Double, longitude: Double) {
        // En una implementación real, aquí mostraríamos un DialogFragment o AlertDialog
        // Para esta demostración, añadiremos directamente un punto predeterminado
        viewModel.addPointOfInterest(
            name = "Nuevo lugar",
            latitude = latitude,
            longitude = longitude,
            category = "Favoritos"
        )

        Toast.makeText(this, "Punto de interés añadido", Toast.LENGTH_SHORT).show()
    }

    @Composable
    fun ExplorationScreen(viewModel: LocationViewModel) {
        // Esta pantalla mostraría estadísticas de exploración, rutas sugeridas, etc.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Progreso de Exploración",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { viewModel.explorationProgress.value ?: 0f },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Rutas Sugeridas",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Aquí iría un LazyColumn con las rutas sugeridas
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ruta de Monumentos",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Visita los principales monumentos de la ciudad",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { /* Mostrar ruta en el mapa */ },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Ver en mapa")
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Zonas por descubrir",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Recorrido por áreas que aún no has explorado",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { /* Mostrar ruta en el mapa */ },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Ver en mapa")
                    }
                }
            }
        }
    }
}