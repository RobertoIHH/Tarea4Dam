package com.example.locationapp.data

import org.json.JSONArray
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ExploredZoneDao {
    @Insert
    suspend fun insert(zone: ExploredZone): Long

    @Update
    suspend fun update(zone: ExploredZone)

    @Delete
    suspend fun delete(zone: ExploredZone)

    @Query("SELECT * FROM explored_zones ORDER BY name ASC")
    fun getAllZones(): LiveData<List<ExploredZone>>

    @Query("SELECT * FROM explored_zones WHERE isDiscovered = 1 ORDER BY discoveredAt DESC")
    fun getDiscoveredZones(): LiveData<List<ExploredZone>>

    @Query("SELECT * FROM explored_zones WHERE id = :zoneId LIMIT 1")
    suspend fun getZoneById(zoneId: Long): ExploredZone?

    @Query("SELECT COUNT(*) FROM explored_zones")
    suspend fun getTotalZoneCount(): Int

    @Query("SELECT COUNT(*) FROM explored_zones WHERE isDiscovered = 1")
    suspend fun getDiscoveredZoneCount(): Int

    @Query("SELECT * FROM explored_zones")
    suspend fun getAllZonesRaw(): List<ExploredZone>

    // Esta función reemplaza getZonesContainingPoint con una implementación que no usa SQL complejo
    suspend fun getZonesContainingPoint(lat: Double, lng: Double): List<ExploredZone> {
        // Primero obtenemos todas las zonas
        val allZones = getAllZonesRaw()

        // Luego filtramos manualmente las que contienen el punto
        return allZones.filter { zone ->
            try {
                // Parseamos las coordenadas de la zona
                val coordinates = JSONArray(zone.coordinates)

                // Obtenemos los límites del polígono
                var minLat = Double.MAX_VALUE
                var maxLat = Double.MIN_VALUE
                var minLng = Double.MAX_VALUE
                var maxLng = Double.MIN_VALUE

                for (i in 0 until coordinates.length()) {
                    val point = coordinates.getJSONArray(i)
                    val pointLat = point.getDouble(0)
                    val pointLng = point.getDouble(1)

                    minLat = minOf(minLat, pointLat)
                    maxLat = maxOf(maxLat, pointLat)
                    minLng = minOf(minLng, pointLng)
                    maxLng = maxOf(maxLng, pointLng)
                }

                // Verificamos si el punto está dentro de los límites
                lat in minLat..maxLat && lng in minLng..maxLng
            } catch (e: Exception) {
                false
            }
        }
    }
}