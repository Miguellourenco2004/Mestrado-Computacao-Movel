package com.example.minequest

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

class MapViewModel : ViewModel() {

    // LOCALIZAÇÃO ATUAL

    private val _lisboaMarkers = MutableStateFlow<List<MapMarker>>(emptyList())
    val lisboaMarkers = _lisboaMarkers.asStateFlow()

    private val _setubalMarkers = MutableStateFlow<List<MapMarker>>(emptyList())
    val setubalMarkers = _setubalMarkers.asStateFlow()

    private val _portugalMarkers = MutableStateFlow<List<MapMarker>>(emptyList())
    val portugalMarkers = _portugalMarkers.asStateFlow()

    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    fun setCurrentLocation(latLng: LatLng) {
        _currentLocation.value = latLng
    }

    // DESTINO
    private val _destination = MutableStateFlow<LatLng?>(null)
    val destination = _destination.asStateFlow()

    fun setDestination(latLng: LatLng?) {
        _destination.value = latLng
    }

    // SUGESTÕES
    private val _predictions = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val predictions = _predictions.asStateFlow()

    fun setPredictions(list: List<Pair<String, String>>) {
        _predictions.value = list
    }

    // ROTA
    private val _routePoints = MutableStateFlow<List<LatLng>>(emptyList())
    val routePoints = _routePoints.asStateFlow()

    // DISTÂNCIA
    private val _distanceText = MutableStateFlow<String?>(null)
    val distanceText = _distanceText.asStateFlow()

    // DURAÇÃO
    private val _durationText = MutableStateFlow<String?>(null)
    val durationText = _durationText.asStateFlow()

    // NAVEGAÇÃO ATIVA
    private val _navigationEnabled = MutableStateFlow(false)
    val navigationEnabled = _navigationEnabled.asStateFlow()

    fun startNavigation() {
        _navigationEnabled.value = true
    }

    fun stopNavigation() {
        _navigationEnabled.value = false
        _routePoints.value = emptyList()
        _distanceText.value = null
        _durationText.value = null
        _destination.value = null
    }

    // === DIRECTIONS API ===
    fun buscarRota(origem: LatLng, destino: LatLng) {
        viewModelScope.launch {
            val url =
                "https://maps.googleapis.com/maps/api/directions/json" +
                        "?origin=${origem.latitude},${origem.longitude}" +
                        "&destination=${destino.latitude},${destino.longitude}" +
                        "&key=AIzaSyDopLW7DqJf2wQG97_iiOuEKpYWj__arpo"

            val request = Request.Builder().url(url).build()
            val client = OkHttpClient()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            val body = response.body?.string() ?: return@launch
            val json = JSONObject(body)

            val status = json.getString("status")
            println("DIRECTIONS STATUS = $status")

            val routes = json.getJSONArray("routes")
            if (routes.length() == 0) return@launch

            val route = routes.getJSONObject(0)

            // PEGAR DISTÂNCIA E TEMPO
            val leg = route.getJSONArray("legs").getJSONObject(0)

            val distanceObj = leg.getJSONObject("distance")
            val durationObj = leg.getJSONObject("duration")

            _distanceText.value = distanceObj.getString("text")
            _durationText.value = durationObj.getString("text")

            // POLYLINE
            val polyline = route
                .getJSONObject("overview_polyline")
                .getString("points")

            val decoded = PolyUtil.decode(polyline)
            println("POLYLINE SIZE = ${decoded.size}")

            _routePoints.value = decoded
        }
    }

    fun loadMarkers(context: Context) {
        val inputStream = context.resources.openRawResource(R.raw.markers)
        val json = inputStream.bufferedReader().use { it.readText() }

        val obj = JSONObject(json)

        fun parseArray(key: String): List<MapMarker> {
            val arr = obj.getJSONArray(key)
            val list = mutableListOf<MapMarker>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    MapMarker(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        lat = o.getDouble("lat"),
                        lng = o.getDouble("lng")
                    )
                )
            }
            return list
        }

        _lisboaMarkers.value = parseArray("lisboa")
        _setubalMarkers.value = parseArray("setubal")
        _portugalMarkers.value = parseArray("portugal")
    }



}
