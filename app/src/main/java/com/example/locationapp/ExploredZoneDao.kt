package com.example.locationapp.data

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

    @Query("SELECT * FROM explored_zones WHERE " +
            "(:lat BETWEEN (SELECT MIN(json_extract(coordinates, '$[*][0]')) FROM json_each(coordinates)) AND " +
            "(SELECT MAX(json_extract(coordinates, '$[*][0]')) FROM json_each(coordinates))) AND " +
            "(:lng BETWEEN (SELECT MIN(json_extract(coordinates, '$[*][1]')) FROM json_each(coordinates)) AND " +
            "(SELECT MAX(json_extract(coordinates, '$[*][1]')) FROM json_each(coordinates)))")
    suspend fun getZonesContainingPoint(lat: Double, lng: Double): List<ExploredZone>
}