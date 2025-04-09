# Aplicación de Localización y Exploración Urbana

Este proyecto implementa una aplicación Android completa para localización, exploración y navegación urbana, desarrollada como parte de los ejercicios solicitados. La aplicación permite a los usuarios visualizar su ubicación actual en mapas interactivos, guardar puntos de interés, explorar zonas urbanas y generar rutas de navegación con simulación de tráfico.

## Características Principales

- Visualización de la ubicación actual mediante OpenStreetMap y Google Maps
- Sistema de exploración urbana con zonas descubribles
- Gestor de puntos de interés personalizados
- Generador de rutas de navegación optimizadas
- Simulador de tráfico para rutas
- Interfaz moderna con Jetpack Compose
- Arquitectura MVVM y persistencia con Room Database

## Requisitos

- Android Studio Iguana (2023.3.1) o superior
- SDK mínimo: Android 7.1+ (API level 25)
- SDK objetivo: Android 14+ (API level 35)
- Dispositivo con servicios de ubicación
- Conexión a Internet

## Configuración del Proyecto

### Paso 1: Clonar o descargar el repositorio

```bash
git clone https://github.com/yourusername/LocationApp.git
cd LocationApp
```

### Paso 2: Abrir el proyecto en Android Studio

1. Abra Android Studio
2. Seleccione "Open an existing Android Studio project"
3. Navegue hasta la carpeta del proyecto y selecciónela
4. Espere a que el proyecto se indexe y sincronice con Gradle

### Paso 3: Limpiar y reconstruir el proyecto

Si encuentras errores relacionados con la caché de Gradle (como el error "Could not read workspace metadata"), sigue estos pasos:

1. Seleccione "Build" > "Clean Project"
2. Seleccione "File" > "Invalidate Caches / Restart"
3. Borre manualmente la carpeta `.gradle` en la raíz del proyecto
4. Vuelva a sincronizar el proyecto: "File" > "Sync Project with Gradle Files"

### Paso 4: Ejecutar la aplicación

1. Conecte un dispositivo Android o configure un emulador
2. Seleccione "Run" > "Run 'app'"
3. La aplicación debería instalarse y ejecutarse en el dispositivo

## Arquitectura de la Aplicación

La aplicación sigue el patrón de arquitectura MVVM (Model-View-ViewModel) con los siguientes componentes:

![Diagrama de arquitectura](architecture_diagram.png)

### Componentes principales:

- **Vista (View)**: Implementada con Jetpack Compose y WebView para mapas
  - `MainActivity.kt`: Actividad principal con navegación por pestañas
  - `RouteScreen.kt`: Pantalla para generación de rutas
  - Componentes de UI en Compose

- **ViewModel**: Gestiona los datos y la lógica de negocio
  - `LocationViewModel.kt`: Maneja la ubicación y los datos de exploración
  - Expone StateFlow y LiveData para la UI

- **Modelo (Model)**: Entidades y repositorios de datos
  - `PointOfInterest.kt`, `ExploredZone.kt`: Entidades principales
  - `PointOfInterestRepository.kt`: Gestiona el acceso a los datos
  - `RouteGenerator.kt`: Encargado de generar rutas optimizadas

- **Persistencia**: Room Database para almacenamiento local
  - `AppDatabase.kt`: Configuración de la base de datos
  - DAOs para acceso a datos: `PointOfInterestDao.kt`, `ExploredZoneDao.kt`

## Funcionalidades Detalladas

### 1. Visualización de Mapas

La aplicación ofrece dos opciones para visualizar mapas:

- **OpenStreetMap**: Implementado con WebView y Leaflet.js
  - Muestra la ubicación actual con un marcador
  - Permite visualizar puntos de interés y zonas de exploración
  - Soporta interacción con el mapa (zoom, desplazamiento)

- **Google Maps**: Implementado como alternativa
  - Utiliza la API web de Google Maps
  - Proporciona una experiencia familiar para los usuarios

### 2. Sistema de Exploración Urbana

- **Zonas Descubribles**: Áreas que se desbloquean al visitarlas físicamente
  - Representadas como polígonos en el mapa
  - Cambian de color al ser descubiertas
  - Contribuyen al progreso de exploración

- **Progreso de Exploración**: Barra que muestra cuánto de la ciudad ha explorado el usuario
  - Se actualiza en tiempo real al descubrir nuevas zonas
  - Proporciona motivación para explorar más áreas

### 3. Gestor de Puntos de Interés

- Permite guardar, categorizar y gestionar puntos de interés personalizados
- Soporte para diferentes categorías (Turismo, Monumentos, Museos, Favoritos, etc.)
- Marcado rápido de ubicaciones con un toque largo en el mapa
- Estado de visitado/no visitado para cada punto

### 4. Generador de Rutas de Navegación

- Genera rutas optimizadas entre puntos seleccionados
- Soporte para diferentes modos de transporte (a pie, bicicleta, automóvil)
- Cálculo de distancia y tiempo estimado para cada ruta
- Visualización clara de la ruta en el mapa

### 5. Simulador de Tráfico

- Simula condiciones de tráfico para rutas generadas
- Ajuste de tiempos estimados basados en patrones de tráfico
- Visualización con códigos de color (fluido, moderado, congestionado)

## Desafíos y Soluciones

### Desafío 1: Integración de OpenStreetMap en WebView

**Problema**: Configurar un WebView para mostrar mapas interactivos con Leaflet.js y permitir la comunicación entre JavaScript y Kotlin.

**Solución**: 
- Implementación de una interfaz JavaScript (`WebAppInterface`) para comunicación bidireccional
- Generación dinámica de HTML con Leaflet incorporado
- Uso de `evaluateJavascript` para actualizar el mapa desde Kotlin

### Desafío 2: Gestión de Permisos de Ubicación

**Problema**: Solicitar y gestionar permisos de ubicación de manera adecuada siguiendo las mejores prácticas de Android.

**Solución**:
- Uso de `ActivityResultContracts` para solicitar permisos
- Manejo de diferentes estados de permisos
- Proporcionar ubicación por defecto cuando no hay permisos o la ubicación no está disponible

### Desafío 3: Algoritmo de Generación de Rutas

**Problema**: Implementar un algoritmo eficiente para generar rutas optimizadas entre múltiples puntos.

**Solución**:
- Implementación de un algoritmo greedy para encontrar el camino más corto
- Uso de la fórmula de Haversine para calcular distancias precisas
- Ajuste de tiempos estimados basados en el modo de transporte y condiciones simuladas

### Desafío 4: Integración de Compose con WebView

**Problema**: Combinar la moderna UI de Jetpack Compose con WebView para mostrar mapas interactivos.

**Solución**:
- Uso de `AndroidView` para integrar WebView en Compose
- Estado compartido entre Compose y WebView mediante ViewModel
- Actualización reactiva del WebView cuando cambian los datos

## Dependencias Utilizadas

- **Kotlin y Jetpack Compose**: Lenguaje principal y framework de UI
  - `androidx.compose:compose-bom`: Bill of Materials para Compose
  - `androidx.compose.ui:ui`: Componentes básicos de UI
  - `androidx.compose.material3:material3`: Material Design 3 para Compose

- **Localización y Mapas**:
  - `com.google.android.gms:play-services-location`: Para obtener la ubicación del dispositivo
  - WebView con Leaflet.js: Para mostrar mapas interactivos

- **Persistence**:
  - `androidx.room:room-runtime`: ORM para acceso a base de datos SQLite
  - `androidx.room:room-ktx`: Extensiones de Kotlin para Room

- **Lifecycle y ViewModel**:
  - `androidx.lifecycle:lifecycle-viewmodel-ktx`: ViewModel con soporte para corrutinas
  - `androidx.lifecycle:lifecycle-livedata-ktx`: LiveData reactiva

- **Herramientas de UI**:
  - `androidx.appcompat:appcompat`: Compatibilidad con versiones anteriores de Android
  - `com.google.android.material:material`: Componentes de Material Design

## Conclusión

Esta aplicación demuestra la implementación de funcionalidades avanzadas de localización y exploración urbana en Android, utilizando tecnologías modernas como Jetpack Compose junto con soluciones clásicas como WebView para la visualización de mapas. La arquitectura MVVM facilita la mantenibilidad del código y la separación de responsabilidades.

## Capturas de Pantalla

*Nota: Incluir capturas de pantalla reales de la aplicación una vez implementada.*

## Créditos

Desarrollado como parte del ejercicio práctico para la asignatura.

## Licencia

MIT License - Ver archivo LICENSE para más detalles.
