# Aplicación de Localización y Exploración Urbana

Esta aplicación Android implementa un sistema completo para localización, exploración y navegación urbana. Permite a los usuarios visualizar su ubicación actual en mapas interactivos, guardar puntos de interés, explorar zonas urbanas y generar rutas de navegación con simulación de tráfico.

## Características Implementadas

- **Múltiples proveedores de mapas**: Soporte para OpenStreetMap y Google Maps
- **Gestión de ubicación**: Obtención y visualización de la ubicación actual del usuario
- **Sistema de exploración urbana**: Zonas descubribles que se desbloquean al visitarlas
- **Gestor de puntos de interés**: Guardado y categorización de ubicaciones favoritas
- **Generador de rutas**: Algoritmo optimizado para crear rutas entre puntos seleccionados
- **Simulador de tráfico**: Ajuste de rutas basado en condiciones de tráfico simuladas
- **Interfaz moderna**: Desarrollada completamente con Jetpack Compose
- **Arquitectura MVVM**: Organización del código siguiendo patrones de diseño recomendados
- **Persistencia con Room**: Almacenamiento local de datos mediante Room Database

## Requisitos Técnicos

- Android Studio Iguana (2023.3.1) o superior
- SDK mínimo: Android 7.1+ (API level 25)
- SDK objetivo: Android 14+ (API level 35)
- Kotlin 2.0.0
- Jetpack Compose con Material3
- Permisos de ubicación y acceso a internet
- Google Play Services Location (21.0.1)

## Estructura del Proyecto

```
com.example.locationapp/
├── data/                 # Entidades y acceso a datos
│   ├── AppDatabase.kt    # Configuración de Room Database
│   ├── PointOfInterest.kt # Entidad para puntos de interés
│   ├── ExploredZone.kt   # Entidad para zonas exploradas
│   └── *Dao.kt           # Interfaces DAO para acceso a datos
├── maps/                 # Proveedores de mapas
│   ├── MapProvider.kt    # Interfaz común para proveedores
│   ├── MapProviderManager.kt # Gestión de proveedores
│   ├── OpenStreetMapProvider.kt # Implementación OSM
│   └── GoogleMapsProvider.kt # Implementación Google Maps
├── routing/              # Generación de rutas
│   └── RouteGenerator.kt # Algoritmo de rutas optimizadas
├── ui/                   # Componentes de UI
│   ├── screens/          # Pantallas de la aplicación
│   │   └── RouteScreen.kt # Pantalla de generación de rutas
│   └── theme/            # Configuración del tema
└── viewmodel/            # ViewModel MVVM
    └── LocationViewModel.kt # Lógica de negocio principal
```

## Configuración e Instalación

### Requisitos previos

- Android Studio Iguana (2023.3.1) o superior
- JDK 11 o superior
- Dispositivo Android (físico o emulador) con API 25 o superior
- Servicios de Google Play actualizados en el dispositivo

### Pasos para compilar y ejecutar

1. **Clonar el repositorio**:
   ```bash
   git clone https://github.com/yourusername/LocationApp.git
   cd LocationApp
   ```

2. **Abrir en Android Studio**:
   - Seleccionar "Open an existing Android Studio project"
   - Navegar hasta la carpeta clonada

3. **Sincronizar con Gradle**:
   - Esperar a que el proyecto se indexe
   - Si hay errores de sincronización:
     ```
     File > Invalidate Caches / Restart
     ```

4. **Ejecutar la aplicación**:
   - Seleccionar un dispositivo o emulador
   - Hacer clic en "Run" (▶️)

### Solución de problemas comunes

- **Error "Could not read workspace metadata"**: 
  - Borrar la carpeta `.gradle` en la raíz del proyecto
  - Ejecutar "Clean Project" y "Rebuild Project"

- **Error de permisos de ubicación**:
  - Verificar que la aplicación tiene concedidos los permisos en la configuración del dispositivo

- **WebView no carga los mapas**:
  - Asegurar que el dispositivo tiene conexión a internet
  - Verificar que JavaScript está habilitado en la configuración

## Detalles de Implementación

### Sistema de Mapas

La aplicación implementa un sistema flexible de proveedores de mapas:

```kotlin
interface MapProvider {
    fun getMapUrl(latitude: Double, longitude: Double, zoom: Int): String
    fun getSearchUrl(query: String): String
    fun getName(): String
}
```

Esto permite cambiar fácilmente entre diferentes proveedores de mapas:

- **OpenStreetMap**: Implementado como opción predeterminada
- **Google Maps**: Disponible como alternativa

El usuario puede cambiar entre proveedores desde la interfaz, y la selección se guarda en SharedPreferences.

### Gestión de Ubicación

La obtención de la ubicación se realiza mediante FusedLocationProviderClient de Google Play Services:

```kotlin
fusedLocationClient.lastLocation
    .addOnSuccessListener { location ->
        if (location != null) {
            viewModel.setCurrentLocation(location.latitude, location.longitude)
        }
    }
```

La aplicación solicita permisos de ubicación utilizando el nuevo sistema de ActivityResultContracts.

### Persistencia de Datos

La aplicación utiliza Room Database para almacenar:

- **Puntos de interés**: Lugares marcados por el usuario
- **Zonas exploradas**: Áreas de la ciudad que el usuario ha visitado

Las consultas a la base de datos se realizan en hilos secundarios mediante corrutinas de Kotlin.

### Generación de Rutas

El algoritmo de generación de rutas implementa:

1. Cálculo de distancias utilizando la fórmula de Haversine
2. Selección optimizada de puntos mediante un algoritmo greedy
3. Estimación de tiempos según el modo de transporte
4. Simulación de tráfico basada en patrones temporales

```kotlin
fun generateRoute(
    points: List<PointOfInterest>,
    startPoint: PointOfInterest? = null,
    mode: TransportMode = TransportMode.WALKING
): Route
```

### Arquitectura MVVM

La aplicación sigue el patrón Model-View-ViewModel:

- **Vista**: Implementada con Jetpack Compose
- **ViewModel**: Gestiona los datos y la lógica de negocio
- **Modelo**: Entidades y repositorios para acceso a datos

La comunicación entre capas utiliza StateFlow y LiveData para reactividad.

## Desarrollado con

- [Kotlin](https://kotlinlang.org/) - Lenguaje principal
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Framework de UI
- [Material3](https://m3.material.io/) - Diseño visual
- [Room Database](https://developer.android.com/training/data-storage/room) - Persistencia
- [Google Play Services Location](https://developers.google.com/android/guides/setup) - Servicios de ubicación
- [ViewModels](https://developer.android.com/topic/libraries/architecture/viewmodel) - Gestión del estado
- [StateFlow y SharedFlow](https://developer.android.com/kotlin/flow) - Programación reactiva

## Futuras Mejoras

- Implementación de mapas nativos con Mapbox o Google Maps SDK
- Sistema de notificaciones basado en geofencing
- Sincronización en la nube de puntos de interés
- Integración con APIs de servicios de transporte público
- Soporte para mapas offline
- Mejora de la precisión de la simulación de tráfico

