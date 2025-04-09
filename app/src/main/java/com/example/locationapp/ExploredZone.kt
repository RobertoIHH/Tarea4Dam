package com.example.locationapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "explored_zones")
data class ExploredZone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val coordinates: String, // JSON de polígono almacenado como string
    val isDiscovered: Boolean = false,
    val discoveredAt: Long? = null
)