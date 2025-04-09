package com.example.locationapp.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PointOfInterestDao {
    @Insert
    suspend fun insert(poi: PointOfInterest): Long

    @Update
    suspend fun update(poi: PointOfInterest)

    @Delete
    suspend fun delete(poi: PointOfInterest)

    @Query("SELECT * FROM points_of_interest ORDER BY createdAt DESC")
    fun getAllPointsOfInterest(): LiveData<List<PointOfInterest>>

    @Query("SELECT * FROM points_of_interest WHERE category = :category ORDER BY name ASC")
    fun getPointsOfInterestByCategory(category: String): LiveData<List<PointOfInterest>>

    @Query("SELECT * FROM points_of_interest WHERE isVisited = 1 ORDER BY name ASC")
    fun getVisitedPoints(): LiveData<List<PointOfInterest>>

    @Query("SELECT * FROM points_of_interest WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLng AND :maxLng")
    fun getPointsInBounds(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): LiveData<List<PointOfInterest>>

    @Query("SELECT * FROM points_of_interest WHERE id = :poiId LIMIT 1")
    suspend fun getPointById(poiId: Long): PointOfInterest?

    @Query("SELECT * FROM points_of_interest WHERE category = :category AND isVisited = 0 ORDER BY name ASC")
    suspend fun getUnvisitedPointsByCategory(category: String): List<PointOfInterest>

    @Query("SELECT * FROM points_of_interest WHERE " +
            "ABS(latitude - :lat) <= :radius AND " +
            "ABS(longitude - :lng) <= :radius")
    suspend fun getNearbyPoints(lat: Double, lng: Double, radius: Double): List<PointOfInterest>
}