package com.example.locationapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.locationapp.data.AppDatabase
import com.example.locationapp.data.ExploredZone
import com.example.locationapp.data.PointOfInterest
import com.example.locationapp.data.PointOfInterestRepository
import com.example.locationapp.maps.MapProviderManager
import com.example.locationapp.routing.RouteGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class LocationViewModel(application: Application) : AndroidViewModel(application) {

    protected val repository: PointOfInterestRepository
    protected val _explorationProgress = MutableLiveData<Float>(0f)

    // StateFlow para usar con Compose
    private val _pointsOfInterest = MutableStateFlow<List<PointOfInterest>>(emptyList())
    val allPointsOfInterest: StateFlow<List<PointOfInterest>> = _pointsOfInterest.asStateFlow()

    private val _exploredZones = MutableStateFlow<List<ExploredZone>>(emptyList())
    val allZones: StateFlow<List<ExploredZone>> = _exploredZones.asStateFlow()

    // LiveData para compatibilidad con vistas tradicionales
    val pointsOfInterestLiveData: LiveData<List<PointOfInterest>>
    val zonesLiveData: LiveData<List<ExploredZone>>

    private val _explorationProgress = MutableLiveData<Float>(0f)
    val explorationProgress: LiveData<Float> = _explorationProgress

    private val _currentLocation = MutableLiveData<Pair<Double, Double>>()
    val currentLocation: LiveData<Pair<Double, Double>> = _currentLocation

    // Para el generador de rutas
    private val _currentRoute = MutableStateFlow<RouteGenerator.Route?>(null)
    val currentRoute: StateFlow<RouteGenerator.Route?> = _currentRoute.asStateFlow()

    // NUEVO: Para la URL del mapa
    private val _urlState = MutableStateFlow("")
    val urlState: StateFlow<String> = _urlState.asStateFlow()

    // NUEVO: Para el proveedor del mapa
    private val _mapProviderKey = MutableStateFlow("openstreetmap")
    val mapProviderKey: StateFlow<String> = _mapProviderKey.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        val pointOfInterestDao = database.pointOfInterestDao()
        val exploredZoneDao = database.exploredZoneDao()
        repository = PointOfInterestRepository(pointOfInterestDao, exploredZoneDao)

        // Inicializar LiveData
        pointsOfInterestLiveData = repository.allPointsOfInterest
        zonesLiveData = repository.allZones

        // Observar cambios en LiveData y actualizar StateFlow
        pointsOfInterestLiveData.observeForever { points ->
            _pointsOfInterest.value = points
        }

        zonesLiveData.observeForever { zones ->
            _exploredZones.value = zones
        }

        // Inicializar con algunas zonas de ejemplo si es la primera vez
        initializeExampleDataIfNeeded()
    }

    // NUEVO: Configurar el proveedor de mapas
    fun setMapProvider(providerKey: String) {
        _mapProviderKey.value = providerKey
        MapProviderManager.setProvider(providerKey)

        // Si tenemos una ubicación actual, actualizar la URL del mapa
        _currentLocation.value?.let { (latitude, longitude) ->
            showLocation(latitude, longitude)
        }
    }

    // NUEVO: Buscar ubicación usando el proveedor actual
    fun searchLocation(query: String) {
        val provider = MapProviderManager.getCurrentProvider()
        viewModelScope.launch {
            if (query.isNotEmpty()) {
                _urlState.value = provider.getSearchUrl(query)
            }
        }
    }

    // NUEVO: Mostrar ubicación en el mapa usando el proveedor actual
    fun showLocation(latitude: Double, longitude: Double, zoom: Int = 15) {
        val provider = MapProviderManager.getCurrentProvider()
        viewModelScope.launch {
            _urlState.value = provider.getMapUrl(latitude, longitude, zoom)
        }
    }

    fun setCurrentLocation(latitude: Double, longitude: Double) {
        _currentLocation.value = Pair(latitude, longitude)

        // NUEVO: Actualizar la URL del mapa con la nueva ubicación
        showLocation(latitude, longitude)

        // Verificar si el usuario ha entrado en alguna zona nueva
        viewModelScope.launch {
            checkZonesAtLocation(latitude, longitude)
        }
    }

    private suspend fun checkZonesAtLocation(latitude: Double, longitude: Double) {
        val exploredZonesDao = AppDatabase.getDatabase(getApplication()).exploredZoneDao()
        val zonesAtLocation = exploredZonesDao.getZonesContainingPoint(latitude, longitude)

        for (zone in zonesAtLocation) {
            if (!zone.isDiscovered) {
                repository.markZoneAsDiscovered(zone.id)
            }
        }

        // Actualizar el progreso de exploración
        updateExplorationProgress()
    }

    private suspend fun updateExplorationProgress() {
        val progress = repository.calculateExplorationProgress()
        _explorationProgress.postValue(progress)
    }

    fun addPointOfInterest(name: String, latitude: Double, longitude: Double, category: String, description: String = "") {
        val newPoi = PointOfInterest(
            name = name,
            latitude = latitude,
            longitude = longitude,
            category = category,
            description = description
        )

        viewModelScope.launch {
            repository.insertPointOfInterest(newPoi)
        }
    }

    fun markPointAsVisited(poiId: Long) {
        viewModelScope.launch {
            val database = AppDatabase.getDatabase(getApplication())
            val poi = database.pointOfInterestDao().getPointById(poiId)
            if (poi != null && !poi.isVisited) {
                val updatedPoi = poi.copy(isVisited = true)
                repository.updatePointOfInterest(updatedPoi)
            }
        }
    }

    fun generateRoute(
        points: List<PointOfInterest>,
        mode: RouteGenerator.TransportMode = RouteGenerator.TransportMode.WALKING
    ) {
        viewModelScope.launch {
            val startPoint = currentLocation.value?.let { (lat, lng) ->
                PointOfInterest(
                    name = "Mi ubicación",
                    latitude = lat,
                    longitude = lng,
                    category = "Actual"
                )
            }

            val routeGenerator = RouteGenerator()
            val route = routeGenerator.generateRoute(
                points = points,
                startPoint = startPoint,
                mode = mode
            )

            _currentRoute.value = route
        }
    }

    fun getRouteForLeaflet(): String {
        val route = currentRoute.value ?: return "[]"

        val pointsArray = JSONArray()
        for (point in route.points) {
            val pointObject = JSONArray()
            pointObject.put(point.latitude)
            pointObject.put(point.longitude)
            pointsArray.put(pointObject)
        }

        return pointsArray.toString()
    }

    fun clearCurrentRoute() {
        _currentRoute.value = null
    }

    private fun initializeExampleDataIfNeeded() {
        viewModelScope.launch {
            val database = AppDatabase.getDatabase(getApplication())
            val zoneCount = database.exploredZoneDao().getTotalZoneCount()

            if (zoneCount == 0) {
                // Crear algunas zonas de ejemplo (ajusta las coordenadas según tu ubicación)
                val madridCenter = createExploredZone(
                    "Centro de Madrid",
                    createPolygon(40.416775, -3.703790, 0.01)
                )

                val plazaMayor = createExploredZone(
                    "Plaza Mayor",
                    createPolygon(40.415421, -3.707021, 0.005)
                )

                val retiro = createExploredZone(
                    "Parque del Retiro",
                    createPolygon(40.414958, -3.682863, 0.015)
                )

                repository.insertExploredZone(madridCenter)
                repository.insertExploredZone(plazaMayor)
                repository.insertExploredZone(retiro)

                // Añadir algunos puntos de interés
                val poi1 = PointOfInterest(
                    name = "Puerta del Sol",
                    latitude = 40.416729,
                    longitude = -3.703339,
                    category = "Turismo"
                )

                val poi2 = PointOfInterest(
                    name = "Palacio Real",
                    latitude = 40.418047,
                    longitude = -3.714187,
                    category = "Monumentos"
                )

                val poi3 = PointOfInterest(
                    name = "Museo del Prado",
                    latitude = 40.413848,
                    longitude = -3.692459,
                    category = "Museos"
                )

                repository.insertPointOfInterest(poi1)
                repository.insertPointOfInterest(poi2)
                repository.insertPointOfInterest(poi3)
            }
        }
    }

    // Crear un polígono rectangular alrededor de un punto central
    private fun createPolygon(centerLat: Double, centerLng: Double, radius: Double): String {
        val points = JSONArray()

        // Crear un cuadrado simple
        points.put(JSONArray().apply {
            put(centerLat - radius)
            put(centerLng - radius)
        })
        points.put(JSONArray().apply {
            put(centerLat + radius)
            put(centerLng - radius)
        })
        points.put(JSONArray().apply {
            put(centerLat + radius)
            put(centerLng + radius)
        })
        points.put(JSONArray().apply {
            put(centerLat - radius)
            put(centerLng + radius)
        })
        // Cerrar el polígono repitiendo el primer punto
        points.put(JSONArray().apply {
            put(centerLat - radius)
            put(centerLng - radius)
        })

        return points.toString()
    }

    private fun createExploredZone(name: String, coordinates: String): ExploredZone {
        return ExploredZone(
            name = name,
            coordinates = coordinates,
            isDiscovered = false
        )
    }

    fun getRecommendedRoutes(): List<List<PointOfInterest>> {
        // Implementar algoritmo para sugerir rutas basadas en zonas no descubiertas
        // Por simplicidad, aquí solo devolvemos un ejemplo
        val routes = mutableListOf<List<PointOfInterest>>()

        viewModelScope.launch {
            val allPois = _pointsOfInterest.value
            if (allPois.size >= 3) {
                // Ruta 1: Monumentos
                val monumentosRoute = allPois.filter { it.category == "Monumentos" }
                if (monumentosRoute.isNotEmpty()) {
                    routes.add(monumentosRoute)
                }

                // Ruta 2: Museos
                val museosRoute = allPois.filter { it.category == "Museos" }
                if (museosRoute.isNotEmpty()) {
                    routes.add(museosRoute)
                }

                // Ruta 3: Puntos no visitados
                val noVisitadosRoute = allPois.filter { !it.isVisited }
                if (noVisitadosRoute.isNotEmpty()) {
                    routes.add(noVisitadosRoute)
                }
            }
        }

        return routes
    }
    fun checkAndUpdateExploredZones(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val database = AppDatabase.getDatabase(getApplication())
            val exploredZonesDao = database.exploredZoneDao()

            val zonesAtLocation = exploredZonesDao.getZonesContainingPoint(latitude, longitude)
            var discoveredNewZone = false

            for (zone in zonesAtLocation) {
                if (!zone.isDiscovered) {
                    repository.markZoneAsDiscovered(zone.id)
                    discoveredNewZone = true
                }
            }

            // Actualizar progreso si se descubrió una nueva zona
            if (discoveredNewZone) {
                val progress = repository.calculateExplorationProgress()
                _explorationProgress.postValue(progress)
            }
        }
    }
}
