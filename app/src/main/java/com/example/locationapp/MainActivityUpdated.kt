package com.example.locationapp

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.locationapp.data.PointOfInterest
import com.example.locationapp.ui.screens.ExploreScreen
import com.example.locationapp.ui.screens.PointsOfInterestManagerScreen
import com.example.locationapp.ui.screens.RouteScreen
import com.example.locationapp.ui.theme.LocationAppTheme
import com.example.locationapp.viewmodel.LocationViewModel
import com.google.android.gms.location.LocationServices

/**
 * Actividad principal que contiene la navegación entre pantallas
 */
class MainActivityUpdated : ComponentActivity() {

    private lateinit var viewModel: LocationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar ViewModel
        viewModel = ViewModelProvider(this)[LocationViewModel::class.java]

        // Inicializar cliente de ubicación
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Solicitar permisos de ubicación
        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (locationGranted) {
                getCurrentLocation(fusedLocationClient)
            }
        }

        // Solicitar permisos al iniciar
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            LocationAppTheme {
                MainScreen(viewModel)
            }
        }
    }

    private fun getCurrentLocation(fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient) {
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {

                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        viewModel.setCurrentLocation(location.latitude, location.longitude)

                        // Comprobar si el usuario ha entrado en nuevas zonas
                        viewModel.checkAndUpdateExploredZones(location.latitude, location.longitude)
                    }
                }
            }
        } catch (e: SecurityException) {
            // Manejar error de permisos
        }
    }
}

/**
 * Pantalla principal con navegación por pestañas
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: LocationViewModel) {
    val navController = rememberNavController()

    // Definir las pestañas de navegación
    val items = listOf(
        Screen.Explore,
        Screen.POIs,
        Screen.Routes,
        Screen.Profile
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                // Evitar múltiples copias de la misma pantalla en el back stack
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Restaurar estado al navegar de vuelta
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Explore.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Explore.route) {
                ExploreScreen(viewModel = viewModel)
            }

            composable(Screen.POIs.route) {
                PointsOfInterestManagerScreen(
                    viewModel = viewModel,
                    onNavigateToMap = { poi ->
                        // Navegar a mapa centrado en este POI
                        navController.navigate(Screen.Explore.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                            // Podríamos pasar argumentos para centrar el mapa en este POI
                        }
                    }
                )
            }

            composable(Screen.Routes.route) {
                RouteScreen(
                    viewModel = viewModel,
                    onGenerateRouteClick = { route ->
                        // Navegar a mapa con la ruta generada
                        navController.navigate(Screen.Explore.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen()
            }
        }
    }
}

/**
 * Pantalla de perfil de usuario simplificada
 */
@Composable
fun ProfileScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(
            text = "Perfil de Usuario",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Implementación pendiente",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Definiciones de las pantallas para la navegación
 */
sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Explore : Screen("explore", "Explorar", Icons.Default.Explore)
    object POIs : Screen("pois", "Mis POIs", Icons.Default.Place)
    object Routes : Screen("routes", "Rutas", Icons.Default.Route)
    object Profile : Screen("profile", "Perfil", Icons.Default.Person)
}