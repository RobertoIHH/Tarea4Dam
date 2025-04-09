package com.example.locationapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.locationapp.data.PointOfInterest
import com.example.locationapp.viewmodel.LocationViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.*

/**
 * Pantalla para gestionar todos los puntos de interés personalizados
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PointsOfInterestManagerScreen(
    viewModel: LocationViewModel = viewModel(),
    onNavigateToMap: (PointOfInterest) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Estados para UI
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todos") }
    var showOnlyUnvisited by remember { mutableStateOf(false) }

    // Estados para diálogos
    var showPoiDetails by remember { mutableStateOf(false) }
    var selectedPoi by remember { mutableStateOf<PointOfInterest?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Observar cambios en los datos
    val allPointsOfInterest by viewModel.allPointsOfInterest.collectAsState(initial = emptyList())

    // Lista de categorías disponibles
    val categories = listOf("Todos", "Turismo", "Monumentos", "Museos", "Restaurantes", "Parques", "Favoritos")

    // Filtrar y ordenar la lista de puntos
    val filteredPoints = allPointsOfInterest
        .filter { poi ->
            // Filtrar por categoría
            (selectedCategory == "Todos" || poi.category == selectedCategory) &&
                    // Filtrar por texto de búsqueda
                    (searchQuery.isEmpty() ||
                            poi.name.contains(searchQuery, ignoreCase = true) ||
                            poi.description.contains(searchQuery, ignoreCase = true)) &&
                    // Filtrar por estado de visita
                    (!showOnlyUnvisited || !poi.isVisited)
        }
        .sortedBy { it.name } // Ordenar por nombre (se podría mejorar con más opciones)

    // Launcher para seleccionar imágenes
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Implementar la lógica para guardar la imagen
            // En una aplicación real, guardaríamos la imagen en el almacenamiento
            // y actualizaríamos el URI en el punto de interés
        }
    }

    Scaffold(
        topBar = {
            Column {
                // Barra superior
                TopAppBar(
                    title = { Text("Mis puntos de interés") },
                    actions = {
                        IconButton(onClick = { showOnlyUnvisited = !showOnlyUnvisited }) {
                            Icon(
                                imageVector = if (showOnlyUnvisited) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = "Mostrar solo no visitados",
                                tint = if (showOnlyUnvisited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )

                // Búsqueda
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Buscar puntos de interés...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )

                // Chips de categorías
                ScrollableTabRow(
                    selectedTabIndex = categories.indexOf(selectedCategory),
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 16.dp,
                    divider = {}
                ) {
                    categories.forEachIndexed { index, category ->
                        Tab(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            CategoryChip(
                                category = category,
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }
                }

                Divider()
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedPoi = null
                    showEditDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir punto de interés")
            }
        }
    ) { paddingValues ->
        if (filteredPoints.isEmpty()) {
            // Mostrar mensaje si no hay resultados
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )

                    Text(
                        text = if (searchQuery.isEmpty() && selectedCategory == "Todos")
                            "No hay puntos de interés guardados"
                        else
                            "No se encontraron resultados",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(16.dp)
                    )

                    if (searchQuery.isNotEmpty() || selectedCategory != "Todos" || showOnlyUnvisited) {
                        Button(
                            onClick = {
                                searchQuery = ""
                                selectedCategory = "Todos"
                                showOnlyUnvisited = false
                            }
                        ) {
                            Text("Limpiar filtros")
                        }
                    }
                }
            }
        } else {
            // Lista de puntos de interés
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(
                    items = filteredPoints,
                    key = { it.id }
                ) { poi ->
                    PointOfInterestCard(
                        poi = poi,
                        onClick = {
                            selectedPoi = poi
                            showPoiDetails = true
                        },
                        onEditClick = {
                            selectedPoi = poi
                            showEditDialog = true
                        },
                        onNavigateClick = {
                            onNavigateToMap(poi)
                        },
                        onToggleVisited = {
                            coroutineScope.launch {
                                if (!poi.isVisited) {
                                    viewModel.markPointAsVisited(poi.id)
                                }
                            }
                        },
                        modifier = Modifier.animateItemPlacement()
                    )
                }

                // Espacio al final para el FAB
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Diálogos
    if (showPoiDetails && selectedPoi != null) {
        PoiDetailsDialog(
            poi = selectedPoi!!,
            onMarkAsVisited = {
                coroutineScope.launch {
                    viewModel.markPointAsVisited(selectedPoi!!.id)
                }
                showPoiDetails = false
            },
            onClose = {
                showPoiDetails = false
            }
        )
    }

    if (showEditDialog) {
        // Diálogo para editar o crear un nuevo POI
        EditPoiDialog(
            poi = selectedPoi,
            onSave = { name, latitude, longitude, category, description ->
                coroutineScope.launch {
                    if (selectedPoi == null) {
                        // Crear nuevo
                        viewModel.addPointOfInterest(name, latitude, longitude, category, description)
                    } else {
                        // TODO: Implementar la actualización de un punto existente
                    }
                }
                showEditDialog = false
            },
            onPickImage = {
                imagePicker.launch("image/*")
            },
            onDismiss = {
                showEditDialog = false
            }
        )
    }

    if (showDeleteConfirmation && selectedPoi != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Eliminar punto de interés") },
            text = { Text("¿Estás seguro de querer eliminar ${selectedPoi!!.name}?") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            // TODO: Implementar eliminación de punto
                        }
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * Tarjeta para mostrar un punto de interés en la lista
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointOfInterestCard(
    poi: PointOfInterest,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onNavigateClick: () -> Unit,
    onToggleVisited: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono o imagen del POI
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        color = when (poi.category) {
                            "Turismo" -> Color(0xFF2196F3)
                            "Monumentos" -> Color(0xFFFFC107)
                            "Museos" -> Color(0xFF9C27B0)
                            "Restaurantes" -> Color(0xFFF44336)
                            "Parques" -> Color(0xFF4CAF50)
                            "Favoritos" -> Color(0xFFE91E63)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (poi.category) {
                        "Turismo" -> Icons.Default.TravelExplore
                        "Monumentos" -> Icons.Default.AccountBalance
                        "Museos" -> Icons.Default.Museum
                        "Restaurantes" -> Icons.Default.Restaurant
                        "Parques" -> Icons.Default.Park
                        "Favoritos" -> Icons.Default.Favorite
                        else -> Icons.Default.Place
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Información del POI
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = poi.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = poi.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    )

                    Text(
                        text = if (poi.isVisited) "Visitado" else "No visitado",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (poi.isVisited)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }

                if (poi.description.isNotBlank()) {
                    Text(
                        text = poi.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Botones de acción
            Column(
                horizontalAlignment = Alignment.End
            ) {
                IconButton(onClick = onNavigateClick) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Navegar a este punto",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar punto",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                if (!poi.isVisited) {
                    IconButton(onClick = onToggleVisited) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = "Marcar como visitado",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Diálogo para editar o crear un punto de interés
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPoiDialog(
    poi: PointOfInterest?,
    onSave: (String, Double, Double, String, String) -> Unit,
    onPickImage: () -> Unit,
    onDismiss: () -> Unit
) {
    // Si poi es null, estamos creando uno nuevo
    val isNew = poi == null

    var name by remember { mutableStateOf(poi?.name ?: "") }
    var latitude by remember { mutableStateOf(poi?.latitude?.toString() ?: "") }
    var longitude by remember { mutableStateOf(poi?.longitude?.toString() ?: "") }
    var category by remember { mutableStateOf(poi?.category ?: "Turismo") }
    var description by remember { mutableStateOf(poi?.description ?: "") }
    var imageUri by remember { mutableStateOf(poi?.imageUri) }

    val categories =
        listOf("Turismo", "Monumentos", "Museos", "Restaurantes", "Parques", "Favoritos")
    var expandedCategory by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 300.dp)
            ) {
                Text(
                    text = if (isNew) "Añadir punto de interés" else "Editar punto de interés",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Nombre
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nombre") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                    }

                    // Categoría
                    item {
                        ExposedDropdownMenuBox(
                            expanded = expandedCategory,
                            onExpandedChange = { expandedCategory = !expandedCategory }
                        ) {
                            OutlinedTextField(
                                value = category,
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
                                categories.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            category = option
                                            expandedCategory = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Coordenadas
                    item {
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
                    }

                    // Descripción
                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Descripción") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .height(100.dp),
                            maxLines = 4
                        )
                    }

                    // Imagen
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onPickImage,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("Seleccionar imagen")
                            }

                            Text(
                                text = if (imageUri != null) "Imagen seleccionada" else "Sin imagen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                            try {
                                val lat = latitude.toDouble()
                                val lng = longitude.toDouble()

                                if (name.isNotBlank()) {
                                    onSave(name, lat, lng, category, description)
                                }
                            } catch (e: Exception) {
                                // Manejar error de conversión
                            }
                        },
                        enabled = name.isNotBlank() && latitude.isNotBlank() && longitude.isNotBlank()
                    ) {
                        Text(if (isNew) "Crear" else "Guardar cambios")
                    }
                }
            }
        }
    }
}
