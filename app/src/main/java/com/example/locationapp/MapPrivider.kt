package com.example.locationapp.maps

interface MapProvider {
    fun getMapUrl(latitude: Double, longitude: Double, zoom: Int): String
    fun getSearchUrl(query: String): String
    fun getName(): String
}
