package com.example.minequest

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.android.compose.*

@SuppressLint("MissingPermission")
@Composable
fun Map(navController: NavController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val placesClient: PlacesClient = remember {
        com.google.android.libraries.places.api.Places.createClient(context)
    }


    var currentLocation by remember { mutableStateOf<LatLng?>(null) }


    var query by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf(listOf<Pair<String, String>>()) } // (placeId, description)


    LaunchedEffect(Unit) {
        if (
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLocation = LatLng(it.latitude, it.longitude)
                }
            }
        }
    }


    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            currentLocation ?: LatLng(0.0, 0.0),
            if (currentLocation != null) 15f else 1f
        )
    }


    fun sugestoes(input: String) {
        if (input.length < 3) {
            predictions = emptyList()
            return
        }

        val token = AutocompleteSessionToken.newInstance()
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(input)
            .setSessionToken(token)
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                predictions = response.autocompletePredictions.map {
                    it.placeId to it.getFullText(null).toString()
                }
            }
            .addOnFailureListener {
                predictions = emptyList()
            }
    }


    fun irParaLugar(placeId: String) {
        val placeRequest = FetchPlaceRequest.newInstance(
            placeId,
            listOf(Place.Field.LAT_LNG, Place.Field.NAME)
        )

        placesClient.fetchPlace(placeRequest)
            .addOnSuccessListener { result ->
                val place = result.place
                place.latLng?.let { latLng ->
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    currentLocation = latLng
                    query = place.name ?: ""
                    predictions = emptyList()
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {


        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 10.dp,
                    color = Color(0xFF6FBF4B),
                    shape = RoundedCornerShape(0.dp)
                )
                .padding(8.dp)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = currentLocation != null,
                    mapStyleOptions = MapStyleOptions.loadRawResourceStyle(
                        context,
                        R.raw.minecraft_style // 👈 referência ao JSON
                    )
                )
            )
            {
                currentLocation?.let {
                    Marker(
                        state = MarkerState(position = it),
                        title = "Você está aqui!"
                    )
                }
            }
        }

        // --- BARRA DE PESQUISA ---
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
                placeholder = { Text("Pesquisar aqui") },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(50.dp))
                    .padding(2.dp),
                shape = RoundedCornerShape(50.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color(0xFF6FBF4B),
                    unfocusedIndicatorColor = Color.Gray,
                    cursorColor = Color(0xFF6FBF4B),
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )

            )


            predictions.forEach { (placeId, description) ->
                TextButton(
                    onClick = { irParaLugar(placeId) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(description, color = Color.Black)
                }
            }
        }
    }
}
