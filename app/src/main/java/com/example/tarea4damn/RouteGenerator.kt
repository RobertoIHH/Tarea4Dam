package com.example.locationapp.routing

import com.example.locationapp.data.PointOfInterest
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RouteGenerator {

    // Constantes para diferentes modos de transporte (metros por minuto)
    companion object {
        const val WALKING_SPEED = 83.3 // 5 km/h
        const val CYCLING_SPEED = 250.0 // 15 km/h
        const val DRIVING_SPEED = 500.0 // 30 km/h
        const val EARTH_RADIUS = 6371000.0 // Radio de la Tierra en metros
    }

    enum class TransportMode {
        WALKING, CYCLING, DRIVING
    }

    data class RouteSegment(
        val startPoint: PointOfInterest,
        val endPoint: PointOfInterest,
        val distance: Double, // en metros
        val estimatedTime: Double // en minutos
    )

    data class Route(
        val points: List<PointOfInterest>,
        val totalDistance: Double, // en metros
        val totalTime: Double, // en minutos
        val transportMode: TransportMode
    )

    /**
     * Calcula la distancia entre dos puntos utilizando la fórmula de Haversine
     * @param p1 Primer punto
     * @param p2 Segundo punto
     * @return Distancia en metros
     */
    fun calculateDistance(p1: PointOfInterest, p2: PointOfInterest): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lon1 = Math.toRadians(p1.longitude)
        val lat2 = Math.toRadians(p2.latitude)
        val lon2 = Math.toRadians(p2.longitude)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS * c
    }

    /**
     * Calcula el tiempo estimado para recorrer una distancia según el modo de transporte
     * @param distance Distancia en metros
     * @param mode Modo de transporte
     * @return Tiempo en minutos
     */
    fun calculateTime(distance: Double, mode: TransportMode): Double {
        return when (mode) {
            TransportMode.WALKING -> distance / WALKING_SPEED
            TransportMode.CYCLING -> distance / CYCLING_SPEED
            TransportMode.DRIVING -> distance / DRIVING_SPEED
        }
    }

    /**
     * Genera una ruta optimizada entre puntos utilizando un algoritmo greedy
     * @param points Lista de puntos a visitar
     * @param startPoint Punto de inicio (opcional)
     * @param mode Modo de transporte
     * @return Ruta optimizada
     */
    fun generateRoute(
        points: List<PointOfInterest>,
        startPoint: PointOfInterest? = null,
        mode: TransportMode = TransportMode.WALKING
    ): Route {
        if (points.isEmpty()) {
            return Route(emptyList(), 0.0, 0.0, mode)
        }

        // Si no hay punto de inicio, usamos el primer punto de la lista
        val start = startPoint ?: points.first()

        // Puntos que aún no se han visitado
        val unvisitedPoints = points.toMutableList()
        if (startPoint != null && startPoint in unvisitedPoints) {
            unvisitedPoints.remove(startPoint)
        }

        // Ruta optimizada
        val routePoints = mutableListOf(start)
        var totalDistance = 0.0
        var totalTime = 0.0

        // Algoritmo greedy: siempre elegir el punto más cercano
        var currentPoint = start
        while (unvisitedPoints.isNotEmpty()) {
            // Encontrar el punto más cercano al punto actual
            val nextPointWithDistance = unvisitedPoints.minByOrNull {
                calculateDistance(currentPoint, it)
            }

            if (nextPointWithDistance != null) {
                val distance = calculateDistance(currentPoint, nextPointWithDistance)
                val time = calculateTime(distance, mode)

                routePoints.add(nextPointWithDistance)
                unvisitedPoints.remove(nextPointWithDistance)

                totalDistance += distance
                totalTime += time

                currentPoint = nextPointWithDistance
            }
        }

        return Route(routePoints, totalDistance, totalTime, mode)
    }

    /**
     * Genera los segmentos individuales de una ruta
     * @param route Ruta completa
     * @return Lista de segmentos de ruta
     */
    fun generateRouteSegments(route: Route): List<RouteSegment> {
        val segments = mutableListOf<RouteSegment>()

        for (i in 0 until route.points.size - 1) {
            val startPoint = route.points[i]
            val endPoint = route.points[i + 1]

            val distance = calculateDistance(startPoint, endPoint)
            val time = calculateTime(distance, route.transportMode)

            segments.add(
                RouteSegment(
                    startPoint = startPoint,
                    endPoint = endPoint,
                    distance = distance,
                    estimatedTime = time
                )
            )
        }

        return segments
    }

    /**
     * Simula el tráfico en una ruta
     * @param segments Segmentos de ruta
     * @return Lista de segmentos con tiempos ajustados por tráfico
     */
    fun simulateTraffic(segments: List<RouteSegment>): List<RouteSegment> {
        val simulatedSegments = mutableListOf<RouteSegment>()

        // Factores de tráfico basados en hora del día
        val calendar = Calendar.getInstance()
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)

        // Factores de congestión por hora (1.0 = normal, >1.0 = congestionado)
        val trafficFactors = when {
            hourOfDay in 7..9 || hourOfDay in 17..19 -> 1.5 // Hora punta
            hourOfDay in 10..16 -> 1.1 // Día laboral
            else -> 1.0 // Noche/madrugada
        }

        // Aplicar factores de tráfico aleatorios a cada segmento
        for (segment in segments) {
            // Generar factor aleatorio de tráfico (±20% alrededor del factor base)
            val randomFactor = trafficFactors * (0.8 + Math.random() * 0.4)

            // Ajustar tiempo estimado basado en factor de tráfico
            val adjustedTime = segment.estimatedTime * randomFactor

            simulatedSegments.add(
                RouteSegment(
                    startPoint = segment.startPoint,
                    endPoint = segment.endPoint,
                    distance = segment.distance,
                    estimatedTime = adjustedTime
                )
            )
        }

        return simulatedSegments
    }

    /**
     * Obtiene el estado de tráfico para cada segmento
     * @param segments Lista de segmentos de ruta
     * @return Mapa de segmentos a estados de tráfico (FLUIDO, MODERADO, CONGESTIONADO)
     */
    fun getTrafficStatus(segments: List<RouteSegment>): Map<RouteSegment, TrafficStatus> {
        val statusMap = mutableMapOf<RouteSegment, TrafficStatus>()

        for (segment in segments) {
            // Calcular velocidad real (metros/minuto)
            val normalSpeed = when {
                segment.distance <= 0 -> 0.0
                else -> segment.distance / segment.estimatedTime
            }

            // Obtener velocidad esperada según modo de transporte
            val expectedSpeed = when {
                segment.startPoint.category == "highway" -> DRIVING_SPEED * 1.2
                segment.startPoint.category == "main_road" -> DRIVING_SPEED
                else -> DRIVING_SPEED * 0.8
            }

            // Calcular ratio de velocidad (real/esperada)
            val speedRatio = normalSpeed / expectedSpeed

            // Determinar estado de tráfico
            val status = when {
                speedRatio >= 0.8 -> TrafficStatus.FLUIDO
                speedRatio >= 0.5 -> TrafficStatus.MODERADO
                else -> TrafficStatus.CONGESTIONADO
            }

            statusMap[segment] = status
        }

        return statusMap
    }

    /**
     * Enumera los posibles estados de tráfico
     */
    enum class TrafficStatus {
        FLUIDO, MODERADO, CONGESTIONADO
    }
}