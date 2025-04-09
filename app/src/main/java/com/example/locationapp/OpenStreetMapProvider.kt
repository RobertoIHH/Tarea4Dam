package com.example.locationapp.maps

class OpenStreetMapProvider : MapProvider {
    override fun getMapUrl(latitude: Double, longitude: Double, zoom: Int): String {
        return "https://www.openstreetmap.org/#map=$zoom/$latitude/$longitude"
    }

    override fun getSearchUrl(query: String): String {
        // Eliminar espacios y caracteres especiales
        val formattedQuery = query.replace(" ", "+").replace(",", "+")
        return "https://www.openstreetmap.org/search?query=$formattedQuery"
    }

    override fun getName(): String {
        return "OpenStreetMap"
    }
}
