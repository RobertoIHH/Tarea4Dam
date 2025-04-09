package com.example.locationapp


import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.locationapp.ui.theme.LocationAppTheme
import com.example.locationapp.viewmodel.LocationViewModel
import com.example.locationapp.maps.MapProviderManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var viewModel: LocationViewModel
    private var webViewReference: WebView? = null
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate iniciado")

        // Inicializar SharedPreferences
        sharedPreferences = getPreferences(Context.MODE_PRIVATE)
        val savedMapProvider = sharedPreferences.getString("map_provider", "openstreetmap")

        // Inicializar el cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        Log.d("MainActivity", "FusedLocationClient inicializado")

        // Inicializar ViewModel
        viewModel = ViewModelProvider(this)[LocationViewModel::class.java]
        viewModel.setMapProvider(savedMapProvider ?: "openstreetmap")
        Log.d("MainActivity", "ViewModel inicializado")

        setContent {
            Log.d("MainActivity", "Configurando UI con Compose")
            LocationAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var selectedTabIndex by remember { mutableStateOf(0) }

                    Column {
                        TabRow(selectedTabIndex = selectedTabIndex) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                text = { Text("Mapa") }
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                text = { Text("Explorar") }
                            )
                            Tab(
                                selected = selectedTabIndex == 2,
                                onClick = { selectedTabIndex = 2 },
                                text = { Text("Perfil") }
                            )
                        }

                        when (selectedTabIndex) {
                            0 -> {
                                MapScreen()
                            }
                            1 -> {
                                ExploreScreen()
                            }
                            2 -> {
                                ProfileScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveMapProviderPreference(providerKey: String) {
        sharedPreferences.edit().apply {
            putString("map_provider", providerKey)
            apply()
        }
    }

    @Composable
    fun MapScreen() {
        val context = LocalContext.current
        val isLoading = remember { mutableStateOf(true) }
        val searchQuery = remember { mutableStateOf("") }
        val coroutineScope = rememberCoroutineScope()

        // Recoger los valores de StateFlow
        val urlState by viewModel.urlState.collectAsState()
        val mapProviderKey by viewModel.mapProviderKey.collectAsState()

        // Este key nos ayudará a recrear el WebView cuando cambie el proveedor
        val webViewKey = remember { mutableStateOf(0) }

        // Observar cambios en mapProviderKey y recrear el WebView
        LaunchedEffect(mapProviderKey) {
            webViewKey.value = webViewKey.value + 1
            isLoading.value = true
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Selector de proveedor de mapas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Elegir mapa:", modifier = Modifier.align(Alignment.CenterVertically))

                MapProviderManager.getAllProviders().forEach { (key, provider) ->
                    Button(
                        onClick = {
                            viewModel.setMapProvider(key)
                            saveMapProviderPreference(key)
                            // No necesitamos forzar la recreación aquí, el LaunchedEffect lo hará
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mapProviderKey == key)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text(provider.getName())
                    }
                }
            }

            // Barra de búsqueda
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery.value,
                    onValueChange = { searchQuery.value = it },
                    placeholder = { Text("Buscar lugares...") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.value.isNotEmpty()) {
                                viewModel.searchLocation(searchQuery.value)
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (searchQuery.value.isNotEmpty()) {
                                viewModel.searchLocation(searchQuery.value)
                            }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                    }
                )
            }

            // WebView
            Box(modifier = Modifier.weight(1f)) {
                // Solicitud de permisos de ubicación
                val locationPermissionRequest = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

                    if (locationGranted) {
                        // Ubicación concedida, obtenemos ubicación actual
                        getCurrentLocation()
                    } else {
                        Toast.makeText(context, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
                    }
                }

                // WebView para mostrar el mapa con la URL del proveedor seleccionado
                // Usamos la key para recrear el WebView cuando cambie el proveedor
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.setGeolocationEnabled(true)

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading.value = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading.value = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    Toast.makeText(
                                        context,
                                        "Error al cargar el mapa: ${error?.description}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    isLoading.value = false
                                }
                            }

                            webViewReference = this

                            // Cargar la URL inicial según el proveedor seleccionado
                            if (urlState.isNotEmpty()) {
                                loadUrl(urlState)
                            } else {
                                // URL por defecto si no hay una seleccionada
                                val provider = MapProviderManager.getCurrentProvider()
                                loadUrl(provider.getMapUrl(40.416775, -3.703790, 13))
                            }
                        }
                    },
                    update = { webView ->
                        // Si necesitas actualizar el WebView en algún momento, puedes hacerlo aquí
                    },
                    modifier = Modifier
                        .fillMaxSize()
                )

                // Botones flotantes
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botón de ubicación actual
                    FloatingActionButton(
                        onClick = {
                            requestLocationPermission(locationPermissionRequest)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Mi ubicación"
                        )
                    }

                    // Botón para guardar ubicación
                    FloatingActionButton(
                        onClick = {
                            // Implementar guardado de ubicación como favorito
                            Toast.makeText(
                                context,
                                "Ubicación guardada como favorito",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bookmarks,
                            contentDescription = "Guardar ubicación"
                        )
                    }

                    // Botón para limpiar marcadores
                    FloatingActionButton(
                        onClick = {
                            // Limpiar marcadores
                            Toast.makeText(
                                context,
                                "Marcadores eliminados",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpiar marcadores"
                        )
                    }

                    // Botón principal para añadir marcador
                    FloatingActionButton(
                        onClick = {
                            // Añadir marcador en la posición actual
                            Toast.makeText(
                                context,
                                "Marcador añadido",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Añadir marcador")
                    }
                }

                // Indicador de carga
                if (isLoading.value) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    private fun requestLocationPermission(permissionLauncher: ActivityResultLauncher<Array<String>>) {
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCoarseLocationPermission || hasFineLocationPermission) {
            // Ya tenemos el permiso, obtenemos la ubicación
            getCurrentLocation()
        } else {
            // Solicitar permisos
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        // Actualizar el ViewModel con la ubicación actual
                        viewModel.setCurrentLocation(location.latitude, location.longitude)
                    } else {
                        Toast.makeText(
                            this,
                            "No se pudo obtener la ubicación actual",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        "Error al obtener ubicación: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    @Composable
    fun ExploreScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "Explorar la ciudad",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { 0.35f },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Has explorado el 35% de las zonas disponibles",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Rutas Sugeridas",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Ruta de ejemplo
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Ruta de Monumentos",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Visita los principales monumentos de la ciudad",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            Toast.makeText(
                                this@MainActivity,
                                "Funcionalidad de visualización de rutas en desarrollo",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Ver en mapa")
                    }
                }
            }
        }
    }

    @Composable
    fun ProfileScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Perfil de Usuario",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "U",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Usuario Explorador",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                "usuario@ejemplo.com",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Preferencias de usuario
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Preferencias",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = true,
                            onCheckedChange = { /* TODO */ }
                        )
                        Text("Notificaciones")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = false,
                            onCheckedChange = { /* TODO */ }
                        )
                        Text("Modo oscuro")
                    }

                    // Preferencia de mapa
                    Text(
                        "Proveedor de mapas preferido:",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val mapProviderKey by viewModel.mapProviderKey.collectAsState()

                        MapProviderManager.getAllProviders().forEach { (key, provider) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = mapProviderKey == key,
                                    onClick = {
                                        viewModel.setMapProvider(key)
                                        saveMapProviderPreference(key)
                                    }
                                )
                                Text(provider.getName())
                            }
                        }
                    }

                    Button(
                        onClick = {
                            Toast.makeText(
                                this@MainActivity,
                                "Configuración guardada",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 16.dp)
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}
