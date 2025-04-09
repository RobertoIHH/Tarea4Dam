package com.example.locationapp.maps

class GoogleMapsProvider : MapProvider {
    override fun getMapUrl(latitude: Double, longitude: Double, zoom: Int): String {
        return "https://www.google.com/maps/@$latitude,$longitude,${zoom}z"
    }

    override fun getSearchUrl(query: String): String {
        // Eliminar espacios y caracteres especiales
        val formattedQuery = query.replace(" ", "+").replace(",", "+")
        return "https://www.google.com/maps/search/$formattedQuery"
    }

    override fun getName(): String {
        return "Google Maps"
    }
}
