package com.example.locationapp.ui.screens

import androidx.compose.runtime.collectAsState
import android.Manifest
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.locationapp.data.PointOfInterest
import com.example.locationapp.maps.WebViewMapHandler
import com.example.locationapp.viewmodel.LocationViewModel
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Pantalla principal de exploración urbana
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: LocationViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Estados para UI
    val currentLatitude = 40.416775 // Madrid por defecto
    val currentLongitude = -3.703790

    // Observar cambios en los datos
    val pointsOfInterest by viewModel.allPointsOfInterest.collectAsState(initial = emptyList())
    val exploredZones by viewModel.allZones.collectAsState(initial = emptyList())
    val currentRoute by viewModel.currentRoute.collectAsState()
    val explorationProgress by viewModel.explorationProgress.collectAsState(initial = 0f)

    // Estados para diálogos
    var showAddPoiDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showPoiDetailsDialog by remember { mutableStateOf(false) }
    var longPressLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var selectedPoiId by remember { mutableStateOf<Long?>(null) }

    // Estado para categoría seleccionada
    var selectedCategory by remember { mutableStateOf("Todos") }

    // Lista de categorías disponibles
    val categories = listOf("Todos", "Turismo", "Monumentos", "Museos", "Restaurantes", "Parques", "Favoritos")

    // Filtrar puntos por categoría seleccionada
    val filteredPoints = if (selectedCategory == "Todos") {
        pointsOfInterest
    } else {
        pointsOfInterest.filter { it.category == selectedCategory }
    }

    // Inicializar el manejador de WebView
    val webViewMapHandler = remember { WebViewMapHandler(context) }

    // Solicitud de permisos de ubicación
    val locationPermissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (locationGranted) {
            // Obtener ubicación actual
            getCurrentLocation(context, viewModel)
        } else {
            Toast.makeText(context, "Se requieren permisos de ubicación para una experiencia completa", Toast.LENGTH_LONG).show()
        }
    }

    // Solicitar permisos al iniciar
    LaunchedEffect(key1 = true) {
        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    // Contenido principal
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Barra superior con título y progreso de exploración
        ExplorationTopBar(
            title = "Exploración Urbana",
            progress = explorationProgress,
            onFilterClick = { showFilterDialog = true }
        )

        // Selector de categorías horizontal
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                CategoryChip(
                    category = category,
                    selected = category == selectedCategory,
                    onClick = { selectedCategory = category }
                )
            }
        }

        // Mapa principal
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // WebView con Leaflet
            webViewMapHandler.LeafletMap(
                modifier = Modifier.fillMaxSize(),
                currentLatitude = currentLatitude,
                currentLongitude = currentLongitude,
                pointsOfInterest = filteredPoints,
                exploredZones = exploredZones,
                routeJsonArray = viewModel.getRouteForLeaflet(),
                onLocationLongPress = { lat, lng ->
                    longPressLocation = Pair(lat, lng)
                    showAddPoiDialog = true
                },
                onPointOfInterestClick = { poiId ->
                    selectedPoiId = poiId
                    showPoiDetailsDialog = true
                }
            )

            // Botones flotantes
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón para mostrar rutas
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            val unvisitedPois = pointsOfInterest.filter { !it.isVisited }
                            if (unvisitedPois.isNotEmpty()) {
                                viewModel.generateRoute(unvisitedPois.take(3))
                                Toast.makeText(context, "Ruta generada a los próximos 3 puntos no visitados", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No hay puntos sin visitar para generar una ruta", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Map, contentDescription = "Generar ruta")
                }

                // Botón de mi ubicación
                FloatingActionButton(
                    onClick = {
                        locationPermissionRequest.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Mi ubicación")
                }

                // Botón principal para añadir POIs
                FloatingActionButton(
                    onClick = {
                        showAddPoiDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir POI")
                }
            }
        }
    }

    // Diálogos
    if (showAddPoiDialog) {
        AddPoiDialog(
            location = longPressLocation,
            onAddPoi = { name, latitude, longitude, category, description ->
                viewModel.addPointOfInterest(name, latitude, longitude, category, description)
                showAddPoiDialog = false
                longPressLocation = null
            },
            onDismiss = {
                showAddPoiDialog = false
                longPressLocation = null
            }
        )
    }

    if (showFilterDialog) {
        FilterDialog(
            categories = categories.filter { it != "Todos" },
            onApplyFilters = { category, onlyUnvisited, sortBy ->
                // Implementar filtrado avanzado
                selectedCategory = category
                showFilterDialog = false
            },
            onDismiss = {
                showFilterDialog = false
            }
        )
    }

    if (showPoiDetailsDialog && selectedPoiId != null) {
        val selectedPoi = pointsOfInterest.find { it.id == selectedPoiId }
        if (selectedPoi != null) {
            PoiDetailsDialog(
                poi = selectedPoi,
                onMarkAsVisited = {
                    viewModel.markPointAsVisited(selectedPoi.id)
                    showPoiDetailsDialog = false
                    selectedPoiId = null
                },
                onClose = {
                    showPoiDetailsDialog = false
                    selectedPoiId = null
                }
            )
        }
    }
}

/**
 * Barra superior con información de exploración
 */
@Composable
fun ExplorationTopBar(
    title: String,
    progress: Float,
    onFilterClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(16.dp)
    ) {
        // Título y botón de filtro
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )

            IconButton(onClick = onFilterClick) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filtrar",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Barra de progreso de exploración
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
            )

            Text(
                text = "Has explorado el ${(progress * 100).toInt()}% de las zonas disponibles",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Chip de categoría para el filtro horizontal
 */
@Composable
fun CategoryChip(
    category: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .padding(4.dp)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = category,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/**
 * Diálogo para añadir un nuevo punto de interés
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPoiDialog(
    location: Pair<Double, Double>?,
    onAddPoi: (String, Double, Double, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Turismo") }
    var description by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf(location?.first?.toString() ?: "") }
    var longitude by remember { mutableStateOf(location?.second?.toString() ?: "") }
    val categories = listOf("Turismo", "Monumentos", "Museos", "Restaurantes", "Parques", "Favoritos")
    var expanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Añadir punto de interés",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                // Categoría con menú desplegable
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .padding(bottom = 8.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    category = option
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Coordenadas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = { Text("Latitud") },
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = { Text("Longitud") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .height(100.dp),
                    maxLines = 4
                )

                // Botones de acción
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            // Validar campos
                            if (name.isNotBlank() && latitude.isNotBlank() && longitude.isNotBlank()) {
                                try {
                                    val lat = latitude.toDouble()
                                    val lng = longitude.toDouble()
                                    onAddPoi(name, lat, lng, category, description)
                                } catch (e: Exception) {
                                    // Manejar error de conversión
                                }
                            }
                        },
                        enabled = name.isNotBlank() && latitude.isNotBlank() && longitude.isNotBlank()
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de detalles de un punto de interés
 */
@Composable
fun PoiDetailsDialog(
    poi: PointOfInterest,
    onMarkAsVisited: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                // Cabecera con nombre y categoría
                Text(
                    text = poi.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = "Categoría: ${poi.category}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Imagen (si existe)
                poi.imageUri?.let { uri ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Imagen no disponible", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Detalles y descripción
                Text(
                    text = "Coordenadas: ${poi.latitude}, ${poi.longitude}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (poi.description.isNotBlank()) {
                    Text(
                        text = "Descripción:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
                    )

                    Text(
                        text = poi.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Estado de visita
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Estado:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    FilterChip(
                        selected = true,
                        onClick = { },
                        label = { Text("Texto") }
                    )
                }

                // Fecha de creación
                Text(
                    text = "Añadido el: ${formatDate(poi.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onClose) {
                        Text("Cerrar")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (!poi.isVisited) {
                        Button(onClick = onMarkAsVisited) {
                            Text("Marcar como visitado")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de filtros avanzados
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    categories: List<String>,
    onApplyFilters: (String, Boolean, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Todos") }
    var onlyUnvisited by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("Nombre") }
    val sortOptions = listOf("Nombre", "Fecha", "Categoría", "Distancia")
    var expandedSort by remember { mutableStateOf(false) }
    var expandedCategory by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Filtros avanzados",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Filtro por categoría
                ExposedDropdownMenuBox(
                    expanded = expandedCategory,
                    onExpandedChange = { expandedCategory = !expandedCategory }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .padding(bottom = 8.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Todos") },
                            onClick = {
                                selectedCategory = "Todos"
                                expandedCategory = false
                            }
                        )

                        categories.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedCategory = option
                                    expandedCategory = false
                                }
                            )
                        }
                    }
                }

                // Filtro por estado de visita
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = onlyUnvisited,
                        onCheckedChange = { onlyUnvisited = it }
                    )

                    Text(
                        text = "Mostrar solo no visitados",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Ordenar por
                ExposedDropdownMenuBox(
                    expanded = expandedSort,
                    onExpandedChange = { expandedSort = !expandedSort }
                ) {
                    OutlinedTextField(
                        value = sortBy,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Ordenar por") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSort)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .padding(vertical = 8.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = expandedSort,
                        onDismissRequest = { expandedSort = false }
                    ) {
                        sortOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    sortBy = option
                                    expandedSort = false
                                }
                            )
                        }
                    }
                }

                // Botones de acción
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            onApplyFilters(selectedCategory, onlyUnvisited, sortBy)
                        }
                    ) {
                        Text("Aplicar filtros")
                    }
                }
            }
        }
    }
}

/**
 * Función para obtener la ubicación actual
 */
private fun getCurrentLocation(context: Context, viewModel: LocationViewModel) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.setCurrentLocation(location.latitude, location.longitude)
                } else {
                    Toast.makeText(
                        context,
                        "No se pudo obtener la ubicación actual",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    context,
                    "Error al obtener ubicación: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}

/**
 * Función para formatear fechas
 */
private fun formatDate(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}