package com.example.locationapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.locationapp.data.PointOfInterest
import com.example.locationapp.routing.RouteGenerator
import com.example.locationapp.viewmodel.LocationViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

@Composable
fun RouteScreen(
    viewModel: LocationViewModel = viewModel(),
    onGenerateRouteClick: (RouteGenerator.Route) -> Unit
) {
    var selectedTransportMode by remember { mutableStateOf(RouteGenerator.TransportMode.WALKING) }
    var selectedPoints by remember { mutableStateOf<List<PointOfInterest>>(emptyList()) }
    val allPointsOfInterest by viewModel.allPointsOfInterest.collectAsState(initial = emptyList())
    var generatedRoute by remember { mutableStateOf<RouteGenerator.Route?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Generador de Rutas",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Selector de modo de transporte
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TransportModeButton(
                text = "A pie",
                selected = selectedTransportMode == RouteGenerator.TransportMode.WALKING,
                onClick = { selectedTransportMode = RouteGenerator.TransportMode.WALKING }
            )

            TransportModeButton(
                text = "Bicicleta",
                selected = selectedTransportMode == RouteGenerator.TransportMode.CYCLING,
                onClick = { selectedTransportMode = RouteGenerator.TransportMode.CYCLING }
            )

            TransportModeButton(
                text = "Auto",
                selected = selectedTransportMode == RouteGenerator.TransportMode.DRIVING,
                onClick = { selectedTransportMode = RouteGenerator.TransportMode.DRIVING }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Seleccionar puntos para la ruta",
            style = MaterialTheme.typography.titleMedium
        )

        // Lista de puntos disponibles para seleccionar
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(allPointsOfInterest) { poi ->
                PointOfInterestItem(
                    poi = poi,
                    isSelected = poi in selectedPoints,
                    onToggleSelection = {
                        selectedPoints = if (poi in selectedPoints) {
                            selectedPoints.filter { it != poi }
                        } else {
                            selectedPoints + poi
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botón para generar ruta
        Button(
            onClick = {
                if (selectedPoints.isNotEmpty()) {
                    val routeGenerator = RouteGenerator()
                    val startPoint = viewModel.currentLocation.value?.let { (lat, lng) ->
                        PointOfInterest(
                            name = "Mi ubicación",
                            latitude = lat,
                            longitude = lng,
                            category = "Actual"
                        )
                    }

                    val route = routeGenerator.generateRoute(
                        points = selectedPoints,
                        startPoint = startPoint,
                        mode = selectedTransportMode
                    )

                    generatedRoute = route
                    onGenerateRouteClick(route)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedPoints.isNotEmpty()
        ) {
            Text("Generar Ruta")
        }

        // Mostrar información de la ruta generada
        generatedRoute?.let { route ->
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Ruta generada",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Distancia total: ${(route.totalDistance / 1000).roundToInt()} km")
                    Text("Tiempo estimado: ${route.totalTime.roundToInt()} minutos")
                    Text("Puntos a visitar: ${route.points.size}")

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onGenerateRouteClick(route) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Ver en mapa")
                    }
                }
            }
        }
    }
}

@Composable
fun TransportModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text)
    }
}

@Composable
fun PointOfInterestItem(
    poi: PointOfInterest,
    isSelected: Boolean,
    onToggleSelection: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = poi.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = poi.category,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() }
            )
        }
    }
}