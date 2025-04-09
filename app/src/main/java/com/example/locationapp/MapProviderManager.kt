package com.example.locationapp.maps

object MapProviderManager {
    private val providers = mapOf(
        "openstreetmap" to OpenStreetMapProvider(),
        "googlemaps" to GoogleMapsProvider()
    )

    private var currentProvider: MapProvider = providers["openstreetmap"]!!

    fun setProvider(providerKey: String) {
        currentProvider = providers[providerKey] ?: providers["openstreetmap"]!!
    }

    fun getCurrentProvider(): MapProvider {
        return currentProvider
    }

    fun getAllProviders(): Map<String, MapProvider> {
        return providers
    }
}
