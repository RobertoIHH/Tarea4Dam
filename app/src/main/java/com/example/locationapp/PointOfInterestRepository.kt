package com.example.locationapp.data

import androidx.lifecycle.LiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PointOfInterestRepository(
    private val pointOfInterestDao: PointOfInterestDao,
    private val exploredZoneDao: ExploredZoneDao
) {
    // Puntos de interés
    val allPointsOfInterest: LiveData<List<PointOfInterest>> = pointOfInterestDao.getAllPointsOfInterest()
    val visitedPoints: LiveData<List<PointOfInterest>> = pointOfInterestDao.getVisitedPoints()

    // Zonas exploradas
    val allZones: LiveData<List<ExploredZone>> = exploredZoneDao.getAllZones()
    val discoveredZones: LiveData<List<ExploredZone>> = exploredZoneDao.getDiscoveredZones()

    suspend fun insertPointOfInterest(poi: PointOfInterest): Long {
        return withContext(Dispatchers.IO) {
            pointOfInterestDao.insert(poi)
        }
    }

    suspend fun updatePointOfInterest(poi: PointOfInterest) {
        withContext(Dispatchers.IO) {
            pointOfInterestDao.update(poi)
        }
    }

    suspend fun deletePointOfInterest(poi: PointOfInterest) {
        withContext(Dispatchers.IO) {
            pointOfInterestDao.delete(poi)
        }
    }

    fun getPointsOfInterestByCategory(category: String): LiveData<List<PointOfInterest>> {
        return pointOfInterestDao.getPointsOfInterestByCategory(category)
    }

    fun getPointsInBounds(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): LiveData<List<PointOfInterest>> {
        return pointOfInterestDao.getPointsInBounds(minLat, maxLat, minLng, maxLng)
    }

    suspend fun insertExploredZone(zone: ExploredZone): Long {
        return withContext(Dispatchers.IO) {
            exploredZoneDao.insert(zone)
        }
    }

    suspend fun updateExploredZone(zone: ExploredZone) {
        withContext(Dispatchers.IO) {
            exploredZoneDao.update(zone)
        }
    }

    suspend fun markZoneAsDiscovered(zoneId: Long) {
        withContext(Dispatchers.IO) {
            val zone = exploredZoneDao.getZoneById(zoneId)
            if (zone != null && !zone.isDiscovered) {
                val updatedZone = zone.copy(
                    isDiscovered = true,
                    discoveredAt = System.currentTimeMillis()
                )
                exploredZoneDao.update(updatedZone)
            }
        }
    }

    suspend fun calculateExplorationProgress(): Float {
        return withContext(Dispatchers.IO) {
            val totalZones = exploredZoneDao.getTotalZoneCount()
            val discoveredZones = exploredZoneDao.getDiscoveredZoneCount()

            if (totalZones == 0) 0f else discoveredZones.toFloat() / totalZones.toFloat()
        }
    }
}