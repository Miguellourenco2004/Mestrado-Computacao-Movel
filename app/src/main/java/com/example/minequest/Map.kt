package com.example.minequest

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@SuppressLint("MissingPermission") // porque verificamos permissão manualmente
@Composable
fun Map(navController: NavController, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // estado reativo da posição atual
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }

    // requisita a última localização conhecida
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

    // posição da câmera (atualiza quando currentLocation muda)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            currentLocation ?: LatLng(0.0, 0.0),
            if (currentLocation != null) 15f else 1f
        )
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = currentLocation != null)
    ) {
        currentLocation?.let {
            Marker(
                state = MarkerState(position = it),
                title = "Você está aqui!"
            )
        }
    }
}
