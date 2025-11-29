package com.example.minequest

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import com.example.minequest.ui.theme.MineQuestFont
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.*
import com.google.android.libraries.places.api.net.*
import com.google.maps.android.compose.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase


@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    navController: NavController,
    viewModel: MapViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {







    val context = LocalContext.current



    // Estados para a cor
    var capturedColor by remember { mutableStateOf<Color?>(null) }
    var showColorDialog by remember { mutableStateOf(false) }

    // ... (código existente de loadMarkers, etc.) ...

    // --- CÂMARA COM PALETTE ---
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            // ✅ Usar a biblioteca Palette para gerar as cores
            Palette.from(bitmap).generate { palette ->
                // O 'dominantSwatch' devolve a cor que aparece em maior quantidade
                val swatch = palette?.dominantSwatch

                // Se preferires cores vivas, podes usar palette?.vibrantSwatch

                if (swatch != null) {
                    capturedColor = Color(swatch.rgb)
                    showColorDialog = true
                } else {
                    Toast.makeText(context, "Não foi possível detetar cor", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

// Launcher para pedir permissão de câmara se necessário
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch()
        } else {
            Toast.makeText(context, "Permissão de câmara necessária", Toast.LENGTH_SHORT).show()
        }
    }



    if (showColorDialog && capturedColor != null) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = {
                Text("Cor Detetada", fontFamily = MineQuestFont, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    // 1. A caixa colorida
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(capturedColor!!, RoundedCornerShape(16.dp))
                            .border(2.dp, Color.Black, RoundedCornerShape(16.dp)) // Opcional: Borda para destaque
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // 2. O texto com o código Hex
                    Text(
                        text = "Hex: #${Integer.toHexString(capturedColor!!.toArgb()).uppercase()}",
                        fontFamily = MineQuestFont,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showColorDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF513220)) // Cor do tema
                ) {
                    Text("OK", fontFamily = MineQuestFont, color = Color.White)
                }
            },
            containerColor = Color.White, // Fundo do diálogo
            shape = RoundedCornerShape(16.dp)
        )
    }


    // Foto do utilizador guardada na BD
    var profileImageName by remember { mutableStateOf("minecraft_creeper_face") }

    // Carregar foto do utilizador do Firebase
    LaunchedEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseDatabase.getInstance().getReference("users")

        auth.currentUser?.let { user ->
            db.child(user.uid).get()
                .addOnSuccessListener { snap ->
                    profileImageName = snap.child("profileImage").getValue(String::class.java)
                        ?: "minecraft_creeper_face"
                }
        }
    }

    // Lista de ícones possíveis para markers
    val markerIcons = listOf(
        R.drawable.arvore, R.drawable.calhao, R.drawable.casas,
        R.drawable.casass, R.drawable.castelo, R.drawable.coiso, R.drawable.fogo
    )

    // Cache para garantir imagem fixa em cada marker
    val markerIconCache = remember { mutableStateMapOf<String, Int>() }

    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    val placesClient = remember { Places.createClient(context) }

    // Estados vindos do ViewModel
    val currentLocation by viewModel.currentLocation.collectAsState()
    val destination by viewModel.destination.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val navigationEnabled by viewModel.navigationEnabled.collectAsState()
    val distanceText by viewModel.distanceText.collectAsState()
    val durationText by viewModel.durationText.collectAsState()

    val lisboaMarkers by viewModel.lisboaMarkers.collectAsState()
    val setubalMarkers by viewModel.setubalMarkers.collectAsState()
    val portugalMarkers by viewModel.portugalMarkers.collectAsState()

    var query by remember { mutableStateOf("") }

    // Carregar markers + localização inicial
    LaunchedEffect(Unit) {
        viewModel.loadMarkers(context)
        viewModel.loadPlayers()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fused.lastLocation.addOnSuccessListener {
                if (it != null) {
                    viewModel.setCurrentLocation(LatLng(it.latitude, it.longitude))
                }
            }
        }
    }

    // Seleciona imagem aleatória mas fixa para cada marker
    fun getIconForMarker(id: String): Int =
        markerIconCache.getOrPut(id) { markerIcons.random() }



    // ---------------------------
    // CÂMARA DO MAPA
    // ---------------------------

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(38.736946, -9.142685), 12f)
    }

    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 16f))
        }

    }



    // ---------------------------
    // BITMAPS PARA MARCADORES
    // ---------------------------

    val markerBitmapCache = remember { mutableMapOf<Int, BitmapDescriptor>() }
    val userBitmapCache = remember { mutableMapOf<Int, BitmapDescriptor>() }

    // Marker do mapa (grande)
    fun bitmapDescriptorMarker(context: Context, resId: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(context, resId)
            ?: return BitmapDescriptorFactory.defaultMarker()

        val px = (96 * context.resources.displayMetrics.density).toInt()

        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(Canvas(bmp))
        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    // Foto do utilizador (pequena)
    fun bitmapDescriptorUser(context: Context, resId: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(context, resId)
            ?: return BitmapDescriptorFactory.defaultMarker()

        val px = (48 * context.resources.displayMetrics.density).toInt()

        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(Canvas(bmp))
        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    fun getMarkerBitmap(resId: Int) =
        markerBitmapCache.getOrPut(resId) { bitmapDescriptorMarker(context, resId) }

    fun getUserBitmap(resId: Int) =
        userBitmapCache.getOrPut(resId) { bitmapDescriptorUser(context, resId) }


    // Imagem correta do utilizador
    fun getUserImage(name: String): Int {
        return when (name) {
            "fb9edad1e26f75" -> R.drawable._fb9edad1e26f75
            "4efed46e89c72955ddc7c77ad08b2ee" -> R.drawable._4efed46e89c72955ddc7c77ad08b2ee
            "578bfd439ef6ee41e103ae82b561986" -> R.drawable._578bfd439ef6ee41e103ae82b561986
            "faf3182a063a0f2a825cb39d959bae7" -> R.drawable._faf3182a063a0f2a825cb39d959bae7
            "a9a4ec03fa9afc407028ca40c20ed774" -> R.drawable.a9a4ec03fa9afc407028ca40c20ed774
            "big_villager_face" -> R.drawable.big_villager_face
            "images" -> R.drawable.images
            else -> R.drawable.minecraft_creeper_face
        }
    }

    val userIconRes = getUserImage(profileImageName)



    // ---------------------------
    // ATUALIZAÇÃO DE LOCALIZAÇÃO
    // ---------------------------

    LaunchedEffect(navigationEnabled) {

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                viewModel.setCurrentLocation(LatLng(loc.latitude, loc.longitude))
            }
        }

        fused.requestLocationUpdates(
            LocationRequest.Builder(2000).setPriority(Priority.PRIORITY_HIGH_ACCURACY).build(),
            callback,
            Looper.getMainLooper()
        )
    }



    // ---------------------------
    // FUNÇÃO addMarker (CORRIGIDA)
    // ---------------------------

    @Composable
    fun AddMarkerComposable(
        m: MapMarker,
        getMarkerBitmap: (Int) -> BitmapDescriptor,
        getIconForMarker: (String) -> Int,
        cameraPositionState: CameraPositionState,
        navigationEnabled: Boolean,
        currentLocation: LatLng?,
        viewModel: MapViewModel
    ) {
        val pos = LatLng(m.lat, m.lng)

        Marker(
            state = MarkerState(pos),
            title = null,
            icon = getMarkerBitmap(getIconForMarker(m.id)),
            onClick = {
                viewModel.setDestination(pos)

                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngZoom(pos, 17f)
                )

                currentLocation?.let { origem ->
                    if (navigationEnabled) viewModel.Rota(origem, pos)
                }

                true // Impede infoWindows e o pin vermelho
            }
        )
    }


    // ---------------------------
    // MAPA
    // ---------------------------

    Box(Modifier.fillMaxSize()) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = false,
                mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.minecraft_style)
            )
        ) {


            // Mostrar posição do utilizador
            currentLocation?.let { pos ->
                Marker(
                    state = MarkerState(pos),
                    title = "Eu",
                    icon = getUserBitmap(userIconRes),
                    anchor = Offset(0.5f, 0.5f)
                )
            }


            val players by viewModel.players.collectAsState()

            players.forEach { user ->
                Marker(
                    state = MarkerState(LatLng(user.lat!!, user.lng!!)),
                    title = user.username ?: "",
                    icon = getUserBitmap(getUserImage(user.profileImage ?: "")),
                    anchor = Offset(0.5f, 0.5f)
                )
            }

            // Destino
            destination?.let { dest ->
                Marker(
                    state = MarkerState(dest),
                    title = null,


                    icon = BitmapDescriptorFactory.fromBitmap(
                        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    ),

                    onClick = { true }
                )
            }


            // Linha da rota
            if (routePoints.isNotEmpty()) {
                Polyline(
                    points = routePoints,
                    width = 16f,
                    color = Color.Red
                )
            }

            // Markers do JSON
            lisboaMarkers.forEach { m ->
                AddMarkerComposable(
                    m = m,
                    getMarkerBitmap = { getMarkerBitmap(it) },
                    getIconForMarker = { getIconForMarker(it) },
                    cameraPositionState = cameraPositionState,
                    navigationEnabled = navigationEnabled,
                    currentLocation = currentLocation,
                    viewModel = viewModel
                )
            }

            setubalMarkers.forEach { m ->
                AddMarkerComposable(
                    m = m,
                    getMarkerBitmap = { getMarkerBitmap(it) },
                    getIconForMarker = { getIconForMarker(it) },
                    cameraPositionState = cameraPositionState,
                    navigationEnabled = navigationEnabled,
                    currentLocation = currentLocation,
                    viewModel = viewModel
                )
            }

            portugalMarkers.forEach { m ->
                AddMarkerComposable(
                    m = m,
                    getMarkerBitmap = { getMarkerBitmap(it) },
                    getIconForMarker = { getIconForMarker(it) },
                    cameraPositionState = cameraPositionState,
                    navigationEnabled = navigationEnabled,
                    currentLocation = currentLocation,
                    viewModel = viewModel
                )
            }

        }



        // ---------------------------
        // BARRA DE PESQUISA
        // ---------------------------

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(20.dp)
                .fillMaxWidth(0.9f)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    sugestoes(it, placesClient, viewModel)
                },
                placeholder = { Text("Pesquisar aqui...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(40.dp)),
                singleLine = true
            )

            predictions.forEach { (id, desc) ->
                TextButton(
                    onClick = {
                        irParaLugar(
                            placeId = id,
                            placesClient = placesClient,
                            cameraPositionState = cameraPositionState,
                            viewModel = viewModel,
                            querySetter = { query = it }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(desc, color = Color.Black)
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(top = 100.dp, end = 16.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    // Verifica permissão antes de abrir
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        cameraLauncher.launch()
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                containerColor = Color.White,
                contentColor = Color(0xFF513220) // Cor castanha do tema
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Abrir Câmara"
                )
            }
        }

        // ---------------------------
        // PAINEL DE DISTÂNCIA/TEMPO
        // ---------------------------

        if (navigationEnabled && distanceText != null && durationText != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
                    .fillMaxWidth(0.8f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Distância: $distanceText")
                    Text("Tempo estimado: $durationText")
                }
            }
        }



        // ---------------------------
        // BOTÃO "IR PARA DESTINO"
        // ---------------------------

        if (destination != null && !navigationEnabled) {
            Button(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                onClick = {
                    viewModel.startNavigation()
                    currentLocation?.let { origem ->
                        destination?.let { dest ->
                            viewModel.Rota(origem, dest)
                        }
                    }
                }
            ) {
                Text("Ir para destino")
            }
        }



        // ---------------------------
        // BOTÃO "CANCELAR VIAGEM"
        // ---------------------------

        if (navigationEnabled) {
            Button(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                onClick = { viewModel.stopNavigation() }
            ) {
                Text("Cancelar viagem")
            }
        }
    }
}



// ---------------------------
// FUNÇÕES AUXILIARES
// ---------------------------

fun sugestoes(
    input: String,
    placesClient: PlacesClient,
    viewModel: MapViewModel
) {
    if (input.length < 3) {
        viewModel.setPredictions(emptyList())
        return
    }

    val token = AutocompleteSessionToken.newInstance()
    val req = FindAutocompletePredictionsRequest.builder()
        .setQuery(input)
        .setSessionToken(token)
        .build()

    placesClient.findAutocompletePredictions(req)
        .addOnSuccessListener { resp ->
            viewModel.setPredictions(
                resp.autocompletePredictions.map {
                    it.placeId to it.getFullText(null).toString()
                }
            )
        }
}

fun irParaLugar(
    placeId: String,
    placesClient: PlacesClient,
    cameraPositionState: CameraPositionState,
    viewModel: MapViewModel,
    querySetter: (String) -> Unit
) {
    val req = FetchPlaceRequest.newInstance(
        placeId,
        listOf(Place.Field.LAT_LNG, Place.Field.NAME)
    )

    placesClient.fetchPlace(req)
        .addOnSuccessListener { res ->
            val latLng = res.place.latLng ?: return@addOnSuccessListener

            querySetter(res.place.name ?: "")
            viewModel.setPredictions(emptyList())
            viewModel.setDestination(latLng)

            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(latLng, 17f)
            )
        }
}
