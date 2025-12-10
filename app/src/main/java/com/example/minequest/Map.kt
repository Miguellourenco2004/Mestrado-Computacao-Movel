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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.minequest.ui.theme.MineQuestFont
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.maps.android.compose.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    navController: NavController,
    viewModel: MapViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {

    //  SETUP DE VARIÁVEIS E ESTADOS

    val context = LocalContext.current
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    val placesClient = remember { Places.createClient(context) }

    // Estados observados do ViewModel
    val currentLocation by viewModel.currentLocation.collectAsState()
    val destination by viewModel.destination.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val navigationEnabled by viewModel.navigationEnabled.collectAsState()
    val distanceText by viewModel.distanceText.collectAsState()
    val durationText by viewModel.durationText.collectAsState()
    val miningResult by viewModel.miningResult.collectAsState()
    val miningError by viewModel.miningError.collectAsState()
    val nearbyMarker by viewModel.nearbyMarker.collectAsState()
    val players by viewModel.players.collectAsState()

    // Listas de Markers
    val lisboaMarkers by viewModel.lisboaMarkers.collectAsState()
    val setubalMarkers by viewModel.setubalMarkers.collectAsState()
    val portugalMarkers by viewModel.portugalMarkers.collectAsState()

    // Estados da UI
    var query by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var profileImageName by remember { mutableStateOf("minecraft_creeper_face") }

    // Camera
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(38.736946, -9.142685), 12f)
    }


    //  PERMISSÕES E LAUNCHERS


    // Launcher da Câmara
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                val swatch = palette?.dominantSwatch ?: palette?.vibrantSwatch
                if (swatch != null) {
                    // Chama o ViewModel para processar a cor
                    viewModel.processCapturedColor(swatch.rgb)
                } else {
                    Toast.makeText(context, "Não foi possível detetar cor", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) cameraLauncher.launch()
        else Toast.makeText(context, context.getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
    }


    // LaunchedEffects


    // Carregar dados iniciais e localização
    LaunchedEffect(Unit) {
        viewModel.loadMarkers(context)
        viewModel.loadPlayers()

        // Foto do user
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseDatabase.getInstance().getReference("users")
        auth.currentUser?.let { user ->
            db.child(user.uid).get().addOnSuccessListener { snap ->
                profileImageName = snap.child("profileImage").getValue(String::class.java) ?: "minecraft_creeper_face"
            }
        }

        // Posição GPS inicial
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fused.lastLocation.addOnSuccessListener {
                if (it != null) viewModel.setCurrentLocation(LatLng(it.latitude, it.longitude))
            }
        }
    }

    // Navegação em tempo real
    LaunchedEffect(navigationEnabled) {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                viewModel.setCurrentLocation(LatLng(loc.latitude, loc.longitude))
            }
        }
        fused.requestLocationUpdates(
            LocationRequest.Builder(2000).setPriority(Priority.PRIORITY_HIGH_ACCURACY).build(),
            callback, Looper.getMainLooper()
        )
    }

    // Controlar erros
    LaunchedEffect(miningError) { if (miningError != null) showErrorDialog = true }

    // Animar câmara quando o destino ou local muda
    LaunchedEffect(destination) {
        destination?.let { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 17f)) }
    }



    //  RECURSOS UI


    val markerBitmapCache = remember { mutableMapOf<Int, BitmapDescriptor>() }
    val userBitmapCache = remember { mutableMapOf<Int, BitmapDescriptor>() }

    fun getMarkerBitmap(resId: Int) = markerBitmapCache.getOrPut(resId) { createBitmapDescriptor(context, resId, 96) }
    fun getUserBitmap(resId: Int) = userBitmapCache.getOrPut(resId) { createBitmapDescriptor(context, resId, 48) }
    val userIconRes = getUserImageResource(profileImageName)


    // INTERFACE VISUAL


    //  Dialogs
    if (showErrorDialog && miningError != null) {
        MiningErrorDialog(miningError!!) { showErrorDialog = false; viewModel.clearMiningError() }
    }
    if (miningResult != null) {
        MiningSuccessDialog(miningResult!!) { viewModel.clearMiningResult() }
    }

    Box(Modifier.fillMaxSize()) {

        // MAPA
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = false,
                mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.minecraft_style)
            )
        ) {
            // Player Local
            currentLocation?.let { pos ->
                Marker(
                    state = MarkerState(pos), title = "Eu",
                    icon = getUserBitmap(userIconRes), anchor = Offset(0.5f, 0.5f)
                )
            }

            // Outros Players
            players.forEach { user ->
                val playerPos = LatLng(user.lat!!, user.lng!!)
                Marker(
                    state = MarkerState(playerPos), title = user.username ?: "",
                    icon = getUserBitmap(getUserImageResource(user.profileImage ?: "")),
                    anchor = Offset(0.5f, 0.5f),
                    onClick = {
                        viewModel.setDestination(playerPos)
                        currentLocation?.let { origem -> if (navigationEnabled) viewModel.Rota(origem, playerPos) }
                        true
                    }
                )
            }

            // Marker de Destino Invisível
            destination?.let { dest ->
                Marker(
                    state = MarkerState(dest), title = null,
                    icon = BitmapDescriptorFactory.fromBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)),
                    onClick = { true }
                )
            }

            // Desenhar a rota
            if (routePoints.isNotEmpty()) {
                Polyline(points = routePoints, width = 16f, color = Color.Red)
            }

            // Marcadores do Mapa
            val allMapMarkers = lisboaMarkers + setubalMarkers + portugalMarkers
            allMapMarkers.forEach { m ->

                val iconId = viewModel.getIconForMarker(m.id)

                // Desenha Círculos
                if (m in lisboaMarkers) {
                    Circle(center = LatLng(m.lat, m.lng), radius = 200.0, strokeColor = Color(0xFF513220), fillColor = Color(0x2252A435), strokeWidth = 2f)
                }

                CustomMapMarker(m, getMarkerBitmap(iconId)) { pos ->
                    viewModel.setDestination(pos)
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(pos, 17f))
                    currentLocation?.let { origem -> if (navigationEnabled) viewModel.Rota(origem, pos) }
                }
            }
        }

        // BARRA DE PESQUISA E OVERLAYS

        Column(modifier = Modifier.align(Alignment.TopCenter).padding(20.dp).fillMaxWidth(0.9f)) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.fetchSuggestions(it, placesClient)
                },
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = MineQuestFont, fontSize = 16.sp, color = Color.Black),
                placeholder = { Text(stringResource(id = R.string.search_here), fontFamily = MineQuestFont, fontSize = 16.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color.Black, unfocusedBorderColor = Color(0xFF513220), cursorColor = Color.Black
                ),
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().background(Color.White, RectangleShape).border(2.dp, Color(0xFF513220), RectangleShape),
                singleLine = true
            )

            // Lista de Sugestões
            predictions.forEach { (id, desc) ->
                TextButton(
                    onClick = {
                        viewModel.selectPlace(id, placesClient) { name -> query = name }
                    },
                    modifier = Modifier.fillMaxWidth().background(Color.White)
                ) { Text(desc, color = Color.Black) }
            }
        }

        // Botão Câmara
        Box(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 1.dp, end = 32.dp)) {
            Image(
                painter = painterResource(id = R.drawable.icone_fotos),
                contentDescription = stringResource(id = R.string.open_camera),
                modifier = Modifier.size(120.dp).clickable {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cameraLauncher.launch()
                    else permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )
        }

        // Painel que aparece quadno do destino
        if (navigationEnabled && distanceText != null && durationText != null) {
            Card(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp).fillMaxWidth(0.6f)
                    .border(width = 4.dp, color = Color(0xFF513220), shape = RectangleShape),
                shape = RoundedCornerShape(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(id = R.string.distance) + "$distanceText", fontFamily = MineQuestFont, fontSize = 16.sp)
                    Text(stringResource(id = R.string.estimated_time) + "$durationText", fontFamily = MineQuestFont, fontSize = 16.sp)
                }
            }
        }

        // Botão ir
        if (destination != null && !navigationEnabled) {
            Button(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                shape = RectangleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF513220), contentColor = Color.White),
                onClick = { viewModel.startNavigation(); currentLocation?.let { origem -> destination?.let { dest -> viewModel.Rota(origem, dest) } } }
            ) { Text(stringResource(id = R.string.go_there), fontFamily = MineQuestFont, fontSize = 16.sp) }
        }

        // Botão cancel
        if (navigationEnabled) {
            Button(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                shape = RectangleShape, colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                onClick = { viewModel.stopNavigation() }
            ) { Text(stringResource(id = R.string.cancel_trip), fontFamily = MineQuestFont, fontSize = 16.sp) }
        }

        // Botão de Mineração de Estrutura
        if (nearbyMarker != null) {
            Button(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).border(width = 3.dp, color = Color(0xFF513220), shape = RectangleShape),
                contentPadding = PaddingValues(8.dp), shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF513220)),
                onClick = {
                    val iconRes = viewModel.getIconForMarker(nearbyMarker!!.id)
                    viewModel.mineBlockFromStructure(iconRes)
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painter = painterResource(id = R.drawable.diamond_pickaxe), contentDescription = "Mine", modifier = Modifier.size(24.dp))
                    Text("MINERAR", fontFamily = MineQuestFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


// HELPERS DE UI


@Composable
fun CustomMapMarker(m: MapMarker, bitmapDescriptor: BitmapDescriptor, onClick: (LatLng) -> Unit) {
    Marker(
        state = MarkerState(LatLng(m.lat, m.lng)),
        icon = bitmapDescriptor,
        onClick = { onClick(LatLng(m.lat, m.lng)); true }
    )
}

@Composable
fun MiningErrorDialog(errorText: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.wait), fontFamily = MineQuestFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.Red) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(errorText, fontFamily = MineQuestFont, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(id = R.string.got_it), fontFamily = MineQuestFont, color = Color.White)
            }
        },
        containerColor = Color.White, shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun MiningSuccessDialog(result: MiningResult, onCollect: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCollect,
        modifier = Modifier.border(4.dp, Color(0xFF513220), RectangleShape), shape = RectangleShape,
        title = { Text(stringResource(id = R.string.block_found), fontFamily = MineQuestFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF513220), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(painterResource(result.imageRes), null, Modifier.size(120.dp).padding(8.dp))
                Text("${result.quantity}x ${result.blockName}", fontFamily = MineQuestFont, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("+${result.xpEarned} XP", fontFamily = MineQuestFont, fontSize = 18.sp, color = Color(0xFFFFA500))
            }
        },
        confirmButton = {
            Button(onClick = onCollect, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF52A435)), modifier = Modifier.fillMaxWidth(), shape = RectangleShape) {
                Text("Collect", fontFamily = MineQuestFont, color = Color.White)
            }
        },
        containerColor = Color.White
    )
}

// Criação de Bitmaps para o Google Maps
fun createBitmapDescriptor(context: Context, resId: Int, sizeDp: Int): BitmapDescriptor {
    val drawable = ContextCompat.getDrawable(context, resId) ?: return BitmapDescriptorFactory.defaultMarker()
    val px = (sizeDp * context.resources.displayMetrics.density).toInt()
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, px, px)
    drawable.draw(Canvas(bmp))
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

fun getUserImageResource(name: String): Int {
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