package com.example.locationapp

import androidx.compose.runtime.*
import androidx.compose.material3.*
import java.util.*
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
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
import com.example.locationapp.routing.RouteGenerator
import com.example.locationapp.ui.screens.RouteScreen
import com.example.locationapp.ui.theme.LocationAppTheme
import com.example.locationapp.viewmodel.LocationViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var viewModel: LocationViewModel
    private var webViewReference: WebView? = null

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
                    MainContent()
                }
            }
        }
    }

    @Composable
    fun MainContent() {
        val coroutineScope = rememberCoroutineScope()
        var currentTab by remember { mutableStateOf(0) }
        var currentLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
        var loading by remember { mutableStateOf(true) }

        // Recolectar flujo de ruta actual para actualizar el mapa
        LaunchedEffect(key1 = Unit) {
            viewModel.currentRoute.collectLatest { route ->
                route?.let {
                    // Actualizar el mapa con la nueva ruta
                    webViewReference?.let { webView ->
                        val routePoints = viewModel.getRouteForLeaflet()
                        val transportMode = it.transportMode.toString()
                        val jsCode = "if (typeof showRoute === 'function') { showRoute('$routePoints', '$transportMode'); }"
                        webView.evaluateJavascript(jsCode, null)
                    }
                }
            }
        }

        // Observar cambios en la ubicación actual
        DisposableEffect(viewModel) {
            val observer = androidx.lifecycle.Observer<Pair<Double, Double>> { location ->
                currentLocation = location
                loading = false
            }
            viewModel.currentLocation.observeForever(observer)

            onDispose {
                viewModel.currentLocation.removeObserver(observer)
            }
        }

        // Solicitud de permisos
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val locationGranted = permissions.entries.all { it.value }
            if (locationGranted) {
                getCurrentLocation()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Se requieren permisos de ubicación para funcionar correctamente",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // Verificar permisos al inicio
        LaunchedEffect(key1 = true) {
            checkLocationPermissions(requestPermissionLauncher)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Tabs para navegación
            TabRow(selectedTabIndex = currentTab) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    text = { Text("Mapa") }
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    text = { Text("Rutas") }
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

                                    // Configurar interfaz JavaScript
                                    addJavascriptInterface(
                                        WebAppInterface(coroutineScope),
                                        "AndroidInterface"
                                    )

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            // Actualizar puntos de interés y zonas cuando la página se carga
                                            viewModel.allPointsOfInterest.value?.let { pois ->
                                                val poisJson = JSONArray().apply {
                                                    pois.forEach { poi ->
                                                        put(org.json.JSONObject().apply {
                                                            put("id", poi.id)
                                                            put("name", poi.name)
                                                            put("lat", poi.latitude)
                                                            put("lng", poi.longitude)
                                                            put("category", poi.category)
                                                            put("visited", poi.isVisited)
                                                        })
                                                    }
                                                }
                                                val updateCode = "if (typeof updatePOIs === 'function') { updatePOIs('${poisJson}'); }"
                                                evaluateJavascript(updateCode, null)
                                            }

                                            viewModel.allZones.value?.let { zones ->
                                                val zonesJson = JSONArray().apply {
                                                    zones.forEach { zone ->
                                                        put(org.json.JSONObject().apply {
                                                            put("id", zone.id)
                                                            put("name", zone.name)
                                                            put("coordinates", zone.coordinates)
                                                            put("discovered", zone.isDiscovered)
                                                        })
                                                    }
                                                }
                                                val updateCode = "if (typeof updateZones === 'function') { updateZones('${zonesJson}'); }"
                                                evaluateJavascript(updateCode, null)
                                            }
                                        }
                                    }

                                    // Guardar referencia al WebView
                                    webViewReference = this
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
                                    showAddPoiDialog(location.first, location.second)
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
                            progress = { viewModel.explorationProgress.value ?: 0f },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .fillMaxWidth(0.8f)
                        )
                    }
                }
                1 -> {
                    // Pantalla de generación de rutas
                    RouteScreen(
                        viewModel = viewModel,
                        onGenerateRouteClick = { route ->
                            // Cambiar a la pestaña del mapa para mostrar la ruta
                            currentTab = 0
                        }
                    )
                }
                2 -> {
                    // Pantalla de exploración y estadísticas
                    ExplorationScreen(viewModel)
                }
            }
        }
    }

    inner class WebAppInterface(private val coroutineScope: androidx.compose.runtime.CoroutineScope) {
        @JavascriptInterface
        fun onMapClick(latitude: Double, longitude: Double) {
            runOnUiThread {
                // Mostrar diálogo para añadir un punto de interés
                showAddPoiDialog(latitude, longitude)
            }
        }

        @JavascriptInterface
        fun onPoiClick(poiId: Long) {
            runOnUiThread {
                // Mostrar detalles del punto de interés
                showPoiDetailDialog(poiId)
            }
        }

        @JavascriptInterface
        fun markPoiAsVisited(poiId: Long) {
            coroutineScope.launch {
                viewModel.markPointAsVisited(poiId)
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
                    .route-marker-label {
                        background-color: transparent;
                        border: none;
                        box-shadow: none;
                        font-weight: bold;
                        font-size: 12px;
                        color: white;
                        margin-top: -4px;
                        margin-left: -4px;
                    }
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
                    var routeLayer = L.layerGroup().addTo(map);
                    
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
                    
                    // Función para mostrar ruta
                    function showRoute(routePoints, transportMode) {
                        // Limpiar capa de ruta anterior
                        routeLayer.clearLayers();
                        
                        // Parsear los puntos de la ruta
                        const points = JSON.parse(routePoints);
                        
                        if (points.length < 2) return;
                        
                        // Crear polilínea para la ruta
                        const routeColor = getTransportModeColor(transportMode);
                        const routeLine = L.polyline(points, {
                            color: routeColor,
                            weight: 5,
                            opacity: 0.7,
                            lineJoin: 'round'
                        }).addTo(routeLayer);
                        
                        // Añadir marcadores para cada punto
                        for (let i = 0; i < points.length; i++) {
                            const isStart = i === 0;
                            const isEnd = i === points.length - 1;
                            
                            let markerColor = '#3388ff';
                            if (isStart) markerColor = '#4CAF50';
                            if (isEnd) markerColor = '#F44336';
                            
                            const marker = L.circleMarker(points[i], {
                                radius: 8,
                                fillColor: markerColor,
                                color: '#fff',
                                weight: 2,
                                opacity: 1,
                                fillOpacity: 0.8
                            }).addTo(routeLayer);
                            
                            // Añadir etiqueta con número de orden
                            marker.bindTooltip((i + 1).toString(), {
                                permanent: true,
                                direction: 'center',
                                className: 'route-marker-label'
                            }).openTooltip();
                        }
                        
                        // Ajustar vista para mostrar toda la ruta
                        map.fitBounds(routeLine.getBounds(), {
                            padding: [50, 50]
                        });
                    }
                    
                    function getTransportModeColor(mode) {
                        switch (mode) {
                            case 'WALKING':
                                return '#4CAF50'; // Verde
                            case 'CYCLING':
                                return '#2196F3'; // Azul
                            case 'DRIVING':
                                return '#F44336'; // Rojo
                            default:
                                return '#3388ff'; // Azul por defecto
                        }
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

    private fun showPoiDetailDialog(poiId: Long) {
        // En una implementación real, aquí mostraríamos un DialogFragment con los detalles
        Toast.makeText(this, "Detalles del punto de interés $poiId", Toast.LENGTH_SHORT).show()
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
                        onClick = {
                            // Generar una ruta con todos los puntos de la categoría "Monumentos"
                            val monumentos = viewModel.allPointsOfInterest.value.filter { it.category == "Monumentos" }
                            if (monumentos.isNotEmpty()) {
                                viewModel.generateRoute(monumentos)
                            }
                        },
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
                        onClick = {
                            // En una implementación real, generaría una ruta por zonas no descubiertas
                            val noVisitados = viewModel.allPointsOfInterest.value.filter { !it.isVisited }
                            if (noVisitados.isNotEmpty()) {
                                viewModel.generateRoute(noVisitados)
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Ver en mapa")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Opción para simular tráfico
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Simulador de Tráfico",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Simula patrones de tráfico en tus rutas",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            // Simulación de tráfico (ejercicio 4, opción B)
                            val route = viewModel.currentRoute.value
                            if (route != null) {
                                val routeGenerator = RouteGenerator()
                                val segments = routeGenerator.generateRouteSegments(route)
                                val simulatedSegments = routeGenerator.simulateTraffic(segments)
                                val trafficStatus = routeGenerator.getTrafficStatus(simulatedSegments)

                                // En una implementación real, mostrarías esto visualmente en el mapa
                                Toast.makeText(
                                    this@MainActivity,
                                    "Simulación de tráfico activada",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Primero genera una ruta",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Simular tráfico")
                    }
                }
            }
        }
    }
}