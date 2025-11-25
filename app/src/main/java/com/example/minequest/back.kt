package com.example.minequest

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MapViewModel : ViewModel() {


    private val _lisboaMarkers = MutableStateFlow<List<MapMarker>>(emptyList())
    val lisboaMarkers = _lisboaMarkers.asStateFlow()

    private val _setubalMarkers = MutableStateFlow<List<MapMarker>>(emptyList())
    val setubalMarkers = _setubalMarkers.asStateFlow()

    private val _portugalMarkers = MutableStateFlow<List<MapMarker>>(emptyList())
    val portugalMarkers = _portugalMarkers.asStateFlow()

    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    private var lastSavedLocation: LatLng? = null

    private val _players = MutableStateFlow<List<User>>(emptyList())

    val players = _players.asStateFlow()


    fun setCurrentLocation(latLng: LatLng) {
        _currentLocation.value = latLng

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance().getReference("users")

        // Se nunca guardámos ainda
        if (lastSavedLocation == null) {
            lastSavedLocation = latLng
            db.child(uid).child("lat").setValue(latLng.latitude)
            db.child(uid).child("lng").setValue(latLng.longitude)
            return
        }

        // Calcular distância (em metros)
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            lastSavedLocation!!.latitude, lastSavedLocation!!.longitude,
            latLng.latitude, latLng.longitude,
            results
        )

        val distanceMoved = results[0]

        // Apenas guardar se andou mais de 20 metros
        if (distanceMoved > 20) {
            lastSavedLocation = latLng
            db.child(uid).child("lat").setValue(latLng.latitude)
            db.child(uid).child("lng").setValue(latLng.longitude)
        }
    }





    fun loadPlayers() {
        val db = FirebaseDatabase.getInstance().getReference("users")
        val myUid = FirebaseAuth.getInstance().currentUser?.uid

        db.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                val list = snapshot.children.mapNotNull { snap ->

                    val user = snap.getValue(User::class.java) ?: return@mapNotNull null

                    if (snap.key == myUid) return@mapNotNull null
                    if (user.lat == null || user.lng == null) return@mapNotNull null

                    user
                }

                _players.value = list
            }

            override fun onCancelled(error: DatabaseError) {}
        })
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
    fun Rota(origem: LatLng, destino: LatLng) {
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
