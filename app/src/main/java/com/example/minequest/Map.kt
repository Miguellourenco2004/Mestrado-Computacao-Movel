package com.example.minequest

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
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

    // Nome da imagem do utilizador carregada do Firebase
    var profileImageName by remember { mutableStateOf("minecraft_creeper_face") }

    // Carregar foto do utilizador do Firebase
    LaunchedEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseDatabase.getInstance().getReference("users")

        auth.currentUser?.let { user ->
            db.child(user.uid).get()
                .addOnSuccessListener { snapshot ->
                    profileImageName = snapshot.child("profileImage")
                        .getValue(String::class.java)
                        ?: "minecraft_creeper_face"
                }
        }
    }

    // Lista de ícones possíveis para markers aleatórios do mapa
    val markerIcons = listOf(
        R.drawable.arvore, R.drawable.calhao, R.drawable.casas,
        R.drawable.casass, R.drawable.castelo, R.drawable.coiso, R.drawable.fogo
    )

    // Cache para garantir que cada marker mantém a mesma imagem aleatória
    val markerIconCache = remember { mutableStateMapOf<String, Int>() }

    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    val placesClient = remember { Places.createClient(context) }

    // Estados do ViewModel
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

    // Carregar markers e localização inicial
    LaunchedEffect(Unit) {
        viewModel.loadMarkers(context)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fused.lastLocation.addOnSuccessListener {
                if (it != null) {
                    viewModel.setCurrentLocation(LatLng(it.latitude, it.longitude))
                }
            }
        }
    }

    fun getIconForMarker(id: String): Int {
        return markerIconCache.getOrPut(id) { markerIcons.random() }
    }

    // Estado inicial da câmara
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(38.736946, -9.142685),
            12f
        )
    }

    // Quando a localização muda, mover câmara
    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(it, 16f)
            )
        }
    }


    // --------------------
    // Funções de Bitmap
    // --------------------

    val markerBitmapCache = remember { mutableMapOf<Int, BitmapDescriptor>() }
    val userBitmapCache = remember { mutableMapOf<Int, BitmapDescriptor>() }

    // Marker grande (96dp)
    fun bitmapDescriptorMarker(context: Context, resId: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(context, resId)
            ?: return BitmapDescriptorFactory.defaultMarker()

        val sizeDp = 96
        val scale = context.resources.displayMetrics.density
        val sizePx = (sizeDp * scale).toInt()

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // Foto do utilizador pequena (48dp)
    fun bitmapDescriptorUser(context: Context, resId: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(context, resId)
            ?: return BitmapDescriptorFactory.defaultMarker()

        val sizeDp = 48
        val scale = context.resources.displayMetrics.density
        val sizePx = (sizeDp * scale).toInt()

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    fun getMarkerBitmap(resId: Int): BitmapDescriptor {
        return markerBitmapCache.getOrPut(resId) {
            bitmapDescriptorMarker(context, resId)
        }
    }

    fun getUserBitmap(resId: Int): BitmapDescriptor {
        return userBitmapCache.getOrPut(resId) {
            bitmapDescriptorUser(context, resId)
        }
    }

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

    // Atualização da localização em tempo real
    LaunchedEffect(navigationEnabled) {

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val pos = LatLng(loc.latitude, loc.longitude)
                viewModel.setCurrentLocation(pos)
            }
        }

        val req = LocationRequest.Builder(2000)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
    }

    // -------------------------
    // MAPA
    // -------------------------

    Box(modifier = Modifier.fillMaxSize()) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = false, // desativar ponto azul
                mapStyleOptions = MapStyleOptions.loadRawResourceStyle(
                    context,
                    R.raw.minecraft_style
                )
            )
        ) {

            // Foto do utilizador como marker que segue a localização
            currentLocation?.let { pos ->
                Marker(
                    state = MarkerState(pos),
                    title = "TU",
                    icon = getUserBitmap(userIconRes),
                    anchor = Offset(0.5f, 0.5f)
                )
            }

            // Destino
            destination?.let {
                Marker(
                    state = MarkerState(it),
                    title = "Destino"
                )
            }

            // Rota
            if (routePoints.isNotEmpty()) {
                Polyline(
                    points = routePoints,
                    width = 20f,
                    color = Color.Red
                )
            }

            // Markers de Lisboa
            lisboaMarkers.forEach { m ->
                Marker(
                    state = MarkerState(LatLng(m.lat, m.lng)),
                    title = m.name,
                    icon = getMarkerBitmap(getIconForMarker(m.id))
                )
            }

            // Markers de Setúbal
            setubalMarkers.forEach { m ->
                Marker(
                    state = MarkerState(LatLng(m.lat, m.lng)),
                    title = m.name,
                    icon = getMarkerBitmap(getIconForMarker(m.id))
                )
            }

            // Markers de Portugal
            portugalMarkers.forEach { m ->
                Marker(
                    state = MarkerState(LatLng(m.lat, m.lng)),
                    title = m.name,
                    icon = getMarkerBitmap(getIconForMarker(m.id))
                )
            }
        }
    }
}
