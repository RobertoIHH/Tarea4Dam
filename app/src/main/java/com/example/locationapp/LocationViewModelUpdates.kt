package com.example.locationapp.viewmodel
import androidx.compose.ui.unit.dp
import android.app.Application
import android.net.Uri
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

/**
 * Esta clase contiene extensiones y nuevas funcionalidades para el LocationViewModel
 * para soportar las características de exploración urbana y gestión de POIs
 */

/**
 * Actualiza un punto de interés existente
 * @param poiId ID del punto a actualizar
 * @param name Nuevo nombre
 * @param latitude Nueva latitud
 * @param longitude Nueva longitud
 * @param category Nueva categoría
 * @param description Nueva descripción
 * @param imageUri Nueva URI de imagen (opcional)
 */
fun LocationViewModel.updatePointOfInterest(
    poiId: Long,
    name: String,
    latitude: Double,
    longitude: Double,
    category: String,
    description: String,
    imageUri: String? = null
) {
    viewModelScope.launch {
        val database = AppDatabase.getDatabase(getApplication())
        val poiDao = database.pointOfInterestDao()

        val existingPoi = poiDao.getPointById(poiId)
        if (existingPoi != null) {
            val updatedPoi = existingPoi.copy(
                name = name,
                latitude = latitude,
                longitude = longitude,
                category = category,
                description = description,
                imageUri = imageUri ?: existingPoi.imageUri
            )

            repository.updatePointOfInterest(updatedPoi)
        }
    }
}

/**
 * Elimina un punto de interés
 * @param poiId ID del punto a eliminar
 */
fun LocationViewModel.deletePointOfInterest(poiId: Long) {
    viewModelScope.launch {
        val database = AppDatabase.getDatabase(getApplication())
        val poiDao = database.pointOfInterestDao()

        val existingPoi = poiDao.getPointById(poiId)
        if (existingPoi != null) {
            repository.deletePointOfInterest(existingPoi)
        }
    }
}

/**
 * Actualiza la URI de la imagen para un punto de interés
 * @param poiId ID del punto a actualizar
 * @param imageUri Nueva URI de imagen
 */
fun LocationViewModel.updatePoiImage(poiId: Long, imageUri: Uri) {
    viewModelScope.launch {
        val database = AppDatabase.getDatabase(getApplication())
        val poiDao = database.pointOfInterestDao()

        val existingPoi = poiDao.getPointById(poiId)
        if (existingPoi != null) {
            val updatedPoi = existingPoi.copy(
                imageUri = imageUri.toString()
            )

            repository.updatePointOfInterest(updatedPoi)
        }
    }
}

/**
 * Filtra los puntos de interés según varios criterios
 * @param category Categoría a filtrar (o "Todos" para todas)
 * @param onlyUnvisited Si es true, solo muestra los no visitados
 * @param query Texto de búsqueda
 * @param sortBy Criterio de ordenación
 * @return Lista filtrada y ordenada de puntos de interés
 */
fun LocationViewModel.getFilteredPointsOfInterest(
    category: String = "Todos",
    onlyUnvisited: Boolean = false,
    query: String = "",
    sortBy: String = "Nombre"
): List<PointOfInterest> {
    val allPois = allPointsOfInterest.value

    // Filtrar
    val filtered = allPois.filter { poi ->
        // Por categoría
        (category == "Todos" || poi.category == category) &&
                // Por estado de visita
                (!onlyUnvisited || !poi.isVisited) &&
                // Por búsqueda de texto
                (query.isEmpty() ||
                        poi.name.contains(query, ignoreCase = true) ||
                        poi.description.contains(query, ignoreCase = true))
    }

    // Ordenar
    return when (sortBy) {
        "Nombre" -> filtered.sortedBy { it.name }
        "Fecha" -> filtered.sortedByDescending { it.createdAt }
        "Categoría" -> filtered.sortedBy { it.category }
        "Distancia" -> {
            val currentLoc = currentLocation.value
            if (currentLoc != null) {
                val (lat, lng) = currentLoc
                filtered.sortedBy { poi ->
                    val distance = calculateDistance(lat, lng, poi.latitude, poi.longitude)
                    distance
                }
            } else {
                filtered.sortedBy { it.name }
            }
        }
        else -> filtered.sortedBy { it.name }
    }
}

/**
 * Calcular la distancia entre dos puntos geográficos usando la fórmula de Haversine
 */
private fun calculateDistance(
    lat1: Double, lon1: Double,
    lat2: Double, lon2: Double
): Double {
    val earthRadius = 6371.0 // Radio de la Tierra en km

    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)

    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)

    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    return earthRadius * c // Distancia en km
}

/**
 * Comprueba si el usuario ha entrado en una zona nueva y la marca como descubierta
 * @param latitude Latitud actual
 * @param longitude Longitud actual
 */
fun LocationViewModel.checkAndUpdateExploredZones(latitude: Double, longitude: Double) {
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

/**
 * Genera rutas recomendadas basadas en zonas no descubiertas y puntos no visitados
 * @return Lista de rutas sugeridas
 */
fun LocationViewModel.generateRecommendedRoutes(): List<List<PointOfInterest>> {
    val routes = mutableListOf<List<PointOfInterest>>()
    val allPois = allPointsOfInterest.value
    val unvisitedPois = allPois.filter { !it.isVisited }

    if (unvisitedPois.isEmpty()) {
        return routes
    }

    // Obtener la ubicación actual
    val currentLoc = currentLocation.value
    if (currentLoc == null) {
        // Si no hay ubicación actual, simplemente devolvemos algunos puntos no visitados
        if (unvisitedPois.size >= 3) {
            routes.add(unvisitedPois.take(3))
        }
        return routes
    }

    // 1. Ruta a puntos cercanos no visitados
    val (lat, lng) = currentLoc
    val nearbyUnvisited = unvisitedPois
        .map { poi -> Pair(poi, calculateDistance(lat, lng, poi.latitude, poi.longitude)) }
        .filter { (_, distance) -> distance < 5.0 } // Dentro de 5 km
        .sortedBy { (_, distance) -> distance }
        .map { (poi, _) -> poi }
        .take(3)

    if (nearbyUnvisited.isNotEmpty()) {
        routes.add(nearbyUnvisited)
    }

    // 2. Ruta por categoría (por ejemplo, todos los monumentos no visitados)
    val categories = unvisitedPois.map { it.category }.distinct()
    for (category in categories) {
        val categoryUnvisited = unvisitedPois.filter { it.category == category }.take(3)
        if (categoryUnvisited.isNotEmpty() && categoryUnvisited.size >= 2) {
            routes.add(categoryUnvisited)
        }
    }

    // 3. Ruta para maximizar la exploración de zonas (puntos en zonas no descubiertas)
    // Esta funcionalidad dependería de un análisis más complejo que requeriría
    // saber qué puntos están en zonas no descubiertas

    return routes
}

/**
 * Crea una notificación local cuando se descubre una nueva zona
 * @param zoneName Nombre de la zona descubierta
 */
fun LocationViewModel.notifyZoneDiscovered(zoneName: String) {
    // Aquí se implementaría la lógica para crear una notificación
    // usando NotificationCompat.Builder
    // Este es un esquema simplificado

    /*
    val notificationManager =
        getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channelId = "zone_discoveries"

    // Crear canal de notificación para Android 8.0+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Descubrimientos de zonas",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    val notificationBuilder = NotificationCompat.Builder(getApplication(), channelId)
        .setSmallIcon(R.drawable.ic_location)
        .setContentTitle("¡Nueva zona descubierta!")
        .setContentText("Has descubierto: $zoneName")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)

    notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    */
}