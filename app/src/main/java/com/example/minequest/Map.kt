package com.example.minequest

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.maps.android.compose.*

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    navController: NavController,
    viewModel: MapViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    val placesClient = remember { com.google.android.libraries.places.api.Places.createClient(context) }

    val currentLocation by viewModel.currentLocation.collectAsState()
    val destination by viewModel.destination.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val navigationEnabled by viewModel.navigationEnabled.collectAsState()
    val distanceText by viewModel.distanceText.collectAsState()
    val durationText by viewModel.durationText.collectAsState()

    var query by remember { mutableStateOf("") }

    // pegar localização inicial
    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            fused.lastLocation.addOnSuccessListener { loc ->
                if (loc != null)
                    viewModel.setCurrentLocation(LatLng(loc.latitude, loc.longitude))
            }
        }
    }

    // atualizar rota conforme te moves
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
            val request = LocationRequest.Builder(2000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build()

            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } else {
            fused.removeLocationUpdates(callback)
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            currentLocation ?: LatLng(0.0, 0.0), 15f
        )
    }

    //-------------------------------------------
    // PESQUISAR
    //-------------------------------------------
    fun sugestoes(input: String) {
        if (input.length < 3) {
            viewModel.setPredictions(emptyList())
            return
        }

        val token = AutocompleteSessionToken.newInstance()
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(input)
            .setSessionToken(token)
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { resp ->
                viewModel.setPredictions(
                    resp.autocompletePredictions.map {
                        it.placeId to it.getFullText(null).toString()
                    }
                )
            }
    }

    //-------------------------------------------
    // IR PARA LUGAR
    //-------------------------------------------
    fun irParaLugar(placeId: String) {
        val req = FetchPlaceRequest.newInstance(
            placeId,
            listOf(Place.Field.LAT_LNG, Place.Field.NAME)
        )

        placesClient.fetchPlace(req)
            .addOnSuccessListener { result ->
                val place = result.place
                val latLng = place.latLng ?: return@addOnSuccessListener

                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngZoom(latLng, 17f)
                )

                query = place.name ?: ""
                viewModel.setPredictions(emptyList())
                viewModel.setDestination(latLng)
            }
    }

    //==========================================
    // UI
    //==========================================

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

            // marcador atual
            currentLocation?.let {
                Marker(state = MarkerState(it), title = "Você está aqui!")
            }

            // marcador destino
            destination?.let {
                Marker(state = MarkerState(it), title = "Destino")
            }

            // rota
            if (routePoints.isNotEmpty()) {
                Polyline(
                    points = routePoints,
                    width = 20f,
                    color = Color.Red,
                    zIndex = 10f
                )
            }
        }

        //------------------------------------------------------------
        // BARRA DE PESQUISA
        //------------------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
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
                shape = RoundedCornerShape(50.dp),
                singleLine = true
            )

            predictions.forEach { (placeId, text) ->
                TextButton(
                    onClick = { irParaLugar(placeId) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text, color = Color.Black)
                }
            }
        }

        //------------------------------------------------------------
        // DISTÂNCIA E TEMPO
        //------------------------------------------------------------
        if (distanceText != null && durationText != null && navigationEnabled) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
                    .fillMaxWidth(0.8f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Distância restante: $distanceText", color = Color.Black)
                    Text("Tempo estimado: $durationText", color = Color.Gray)
                }
            }
        }

        //------------------------------------------------------------
        // BOTÕES NAVEGAÇÃO
        //------------------------------------------------------------

        // iniciar viagem
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

        // cancelar viagem
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
