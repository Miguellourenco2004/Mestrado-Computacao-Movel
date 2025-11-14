package com.example.minequest

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.*
import com.google.android.libraries.places.api.net.*
import com.google.maps.android.compose.*

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    navController: NavController,
    viewModel: MapViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    val placesClient = remember { Places.createClient(context) }

    // VIEWMODEL STATES
    val currentLocation by viewModel.currentLocation.collectAsState()
    val destination by viewModel.destination.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val navigationEnabled by viewModel.navigationEnabled.collectAsState()
    val distanceText by viewModel.distanceText.collectAsState()
    val durationText by viewModel.durationText.collectAsState()

    // MARKERS JSON
    val lisboaMarkers by viewModel.lisboaMarkers.collectAsState()
    val setubalMarkers by viewModel.setubalMarkers.collectAsState()
    val portugalMarkers by viewModel.portugalMarkers.collectAsState()

    var query by remember { mutableStateOf("") }

    // LOAD USER LOCATION + JSON MARKERS
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

    // CAMERA STATE
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(38.736946, -9.142685), // fallback LISBOA
            12f
        )
    }

    // AUTO-MOVE CAMERA WHEN LOCATION LOADED
    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(it, 16f)
            )
        }
    }

    // LIVE NAVIGATION UPDATES
    LaunchedEffect(navigationEnabled) {

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!navigationEnabled) return
                val loc = result.lastLocation ?: return

                val pos = LatLng(loc.latitude, loc.longitude)
                viewModel.setCurrentLocation(pos)

                destination?.let { dest ->
                    viewModel.buscarRota(pos, dest)
                }
            }
        }

        if (navigationEnabled) {
            val req = LocationRequest.Builder(2000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build()

            fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        } else {
            fused.removeLocationUpdates(callback)
        }
    }

    // SEARCH SUGGESTIONS
    fun sugestoes(input: String) {
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

    // GO TO SELECTED PLACE
    fun irParaLugar(placeId: String) {
        val req = FetchPlaceRequest.newInstance(
            placeId,
            listOf(Place.Field.LAT_LNG, Place.Field.NAME)
        )

        placesClient.fetchPlace(req)
            .addOnSuccessListener { res ->
                val latLng = res.place.latLng ?: return@addOnSuccessListener

                query = res.place.name ?: ""
                viewModel.setPredictions(emptyList())
                viewModel.setDestination(latLng)

                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngZoom(latLng, 17f)
                )

            }
    }

    // UI MAP
    Box(modifier = Modifier.fillMaxSize()) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = currentLocation != null,
                mapStyleOptions = MapStyleOptions.loadRawResourceStyle(
                    context,
                    R.raw.minecraft_style
                )
            )
        ) {

            // USER MARKER
            currentLocation?.let {
                Marker(
                    state = MarkerState(it),
                    title = "Você está aqui!"
                )
            }

            // DESTINATION MARKER (IMPORTANT!)
            destination?.let {
                Marker(
                    state = MarkerState(it),
                    title = "Destino"
                )
            }

            // ROUTE POLYLINE
            if (routePoints.isNotEmpty()) {
                Polyline(
                    points = routePoints,
                    width = 20f,
                    color = Color.Red
                )
            }

            // MARKERS FROM JSON
            lisboaMarkers.forEach {
                Marker(
                    state = MarkerState(LatLng(it.lat, it.lng)),
                    title = it.name
                )
            }

            setubalMarkers.forEach {
                Marker(
                    state = MarkerState(LatLng(it.lat, it.lng)),
                    title = it.name
                )
            }

            portugalMarkers.forEach {
                Marker(
                    state = MarkerState(LatLng(it.lat, it.lng)),
                    title = it.name
                )
            }
        }

        // SEARCH BAR
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
                    sugestoes(it)
                },
                placeholder = { Text("Pesquisar aqui...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(50.dp)),
                singleLine = true
            )

            predictions.forEach { (id, desc) ->
                TextButton(
                    onClick = { irParaLugar(id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(desc, color = Color.Black)
                }
            }
        }

        // DISTANCE + TIME PANEL
        if (navigationEnabled && distanceText != null && durationText != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
                    .fillMaxWidth(0.8f),
                shape = RoundedCornerShape(12.dp)
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

        // START NAV
        if (destination != null && !navigationEnabled) {
            Button(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                onClick = {
                    viewModel.startNavigation()
                    currentLocation?.let { origem ->
                        destination?.let { dest ->
                            viewModel.buscarRota(origem, dest)
                        }
                    }
                }
            ) {
                Text("Ir para destino")
            }
        }

        // CANCEL NAVIGATION
        if (navigationEnabled) {
            Button(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                onClick = {
                    viewModel.stopNavigation()
                }
            ) {
                Text("Cancelar viagem")
            }
        }
    }
}
