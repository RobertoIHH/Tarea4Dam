package com.example.locationapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "points_of_interest")
data class PointOfInterest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val description: String = "",
    val imageUri: String? = null,
    val isVisited: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)