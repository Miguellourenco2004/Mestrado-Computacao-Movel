package com.example.minequest

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minequest.model.User
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
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
import kotlin.random.Random
import android.graphics.Color as AndroidColor

// Classe de dados para resultados da mineração
data class MiningResult(
    val blockId: String,
    val blockName: String,
    val quantity: Int,
    val xpEarned: Int,
    val imageRes: Int
)

class MapViewModel : ViewModel() {


    //  VARIÁVEIS


    // Markers e Jogadores
    private val _lisboaMarkers = MutableStateFlow<List<MapMarker>>(emptyList())
    val lisboaMarkers = _lisboaMarkers.asStateFlow()

    private val _setubalMarkers = MutableStateFlow<List<MapMarker>>(emptyList())
    val setubalMarkers = _setubalMarkers.asStateFlow()

    private val _portugalMarkers = MutableStateFlow<List<MapMarker>>(emptyList())
    val portugalMarkers = _portugalMarkers.asStateFlow()

    private val _players = MutableStateFlow<List<User>>(emptyList())
    val players = _players.asStateFlow()

    // Navegação e Localização
    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    private val _destination = MutableStateFlow<LatLng?>(null)
    val destination = _destination.asStateFlow()

    private val _predictions = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val predictions = _predictions.asStateFlow()

    private val _routePoints = MutableStateFlow<List<LatLng>>(emptyList())
    val routePoints = _routePoints.asStateFlow()

    private val _distanceText = MutableStateFlow<String?>(null)
    val distanceText = _distanceText.asStateFlow()

    private val _durationText = MutableStateFlow<String?>(null)
    val durationText = _durationText.asStateFlow()

    private val _navigationEnabled = MutableStateFlow(false)
    val navigationEnabled = _navigationEnabled.asStateFlow()

    // Sistema de Mineração-
    private val _nearbyMarker = MutableStateFlow<MapMarker?>(null)
    val nearbyMarker = _nearbyMarker.asStateFlow()

    private val _miningResult = MutableStateFlow<MiningResult?>(null)
    val miningResult = _miningResult.asStateFlow()

    private val _miningError = MutableStateFlow<String?>(null)
    val miningError = _miningError.asStateFlow()


    private var lastSavedLocation: LatLng? = null
    private var currentUserData: User? = null
    private val INTERACTION_RADIUS_METERS = 200.0


    private val markerIconCache = mutableMapOf<String, Int>()
    private val availableIcons = listOf(
        R.drawable.arvore, R.drawable.calhao, R.drawable.casas,
        R.drawable.casass, R.drawable.castelo, R.drawable.coiso, R.drawable.fogo
    )


    // INIT

    init {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseDatabase.getInstance().getReference("users").child(uid)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        currentUserData = snapshot.getValue(User::class.java)
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }


    //  GESTÃO DE MARCADORES E ÍCONES


    fun loadMarkers(context: Context) {
        try {
            val inputStream = context.resources.openRawResource(R.raw.markers)
            val json = inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(json)

            fun parseArray(key: String): List<MapMarker> {
                if (!obj.has(key)) return emptyList()
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Retorna um ícone fixo para um dado ID de marcador
    fun getIconForMarker(markerId: String): Int {
        return markerIconCache.getOrPut(markerId) {
            availableIcons.random()
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


    //  LOCALIZAÇÃO E GOOGLE PLACES API


    fun setCurrentLocation(latLng: LatLng) {
        _currentLocation.value = latLng
        checkProximityToMarkers(latLng)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance().getReference("users")

        if (lastSavedLocation == null) {
            lastSavedLocation = latLng
            saveLocationToFirebase(db, uid, latLng)
            return
        }

        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            lastSavedLocation!!.latitude, lastSavedLocation!!.longitude,
            latLng.latitude, latLng.longitude,
            results
        )

        if (results[0] > 20) {
            lastSavedLocation = latLng
            saveLocationToFirebase(db, uid, latLng)
        }
    }

    // Pesquisa de locais
    fun fetchSuggestions(query: String, placesClient: PlacesClient) {
        if (query.length < 3) {
            _predictions.value = emptyList()
            return
        }
        val token = AutocompleteSessionToken.newInstance()
        val req = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(token)
            .build()

        placesClient.findAutocompletePredictions(req).addOnSuccessListener { resp ->
            _predictions.value = resp.autocompletePredictions.map {
                it.placeId to it.getFullText(null).toString()
            }
        }.addOnFailureListener {
            _predictions.value = emptyList()
        }
    }

    // Seleção de local (
    fun selectPlace(placeId: String, placesClient: PlacesClient, onPlaceSelected: (String) -> Unit) {
        val req = FetchPlaceRequest.newInstance(placeId, listOf(Place.Field.LAT_LNG, Place.Field.NAME))
        placesClient.fetchPlace(req).addOnSuccessListener { res ->
            val latLng = res.place.latLng ?: return@addOnSuccessListener
            _predictions.value = emptyList()
            setDestination(latLng)
            onPlaceSelected(res.place.name ?: "")
        }
    }

    private fun saveLocationToFirebase(db: DatabaseReference, uid: String, latLng: LatLng) {
        db.child(uid).child("lat").setValue(latLng.latitude)
        db.child(uid).child("lng").setValue(latLng.longitude)
    }

    private fun checkProximityToMarkers(userLocation: LatLng) {
        val allMarkers = _lisboaMarkers.value + _setubalMarkers.value + _portugalMarkers.value
        var closest: MapMarker? = null
        var minDistance = Float.MAX_VALUE

        for (marker in allMarkers) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                userLocation.latitude, userLocation.longitude,
                marker.lat, marker.lng,
                results
            )
            val distance = results[0]

            if (distance <= INTERACTION_RADIUS_METERS && distance < minDistance) {
                minDistance = distance
                closest = marker
            }
        }
        _nearbyMarker.value = closest
    }


    //NAVEGAÇÃO

    fun setDestination(latLng: LatLng?) { _destination.value = latLng }
    fun setPredictions(list: List<Pair<String, String>>) { _predictions.value = list }
    fun startNavigation() { _navigationEnabled.value = true }

    fun stopNavigation() {
        _navigationEnabled.value = false
        _routePoints.value = emptyList()
        _distanceText.value = null
        _durationText.value = null
        _destination.value = null
    }

    fun Rota(origem: LatLng, destino: LatLng) {
        viewModelScope.launch {
            try {

                val url = "https://maps.googleapis.com/maps/api/directions/json" +
                        "?origin=${origem.latitude},${origem.longitude}" +
                        "&destination=${destino.latitude},${destino.longitude}" +
                        "&mode=walking" +
                        "&key=AIzaSyDopLW7DqJf2wQG97_iiOuEKpYWj__arpo"

                val request = Request.Builder().url(url).build()
                val client = OkHttpClient()

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                val body = response.body?.string() ?: return@launch
                val json = JSONObject(body)
                val routes = json.optJSONArray("routes")
                if (routes == null || routes.length() == 0) return@launch

                val route = routes.getJSONObject(0)
                val leg = route.getJSONArray("legs").getJSONObject(0)

                _distanceText.value = leg.getJSONObject("distance").getString("text")
                _durationText.value = leg.getJSONObject("duration").getString("text")

                val polyline = route.getJSONObject("overview_polyline").getString("points")
                _routePoints.value = PolyUtil.decode(polyline)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    // SISTEMA DE MINERAÇÃO


    // Processa a cor vinda da câmara, calcula o nome e inicia a mineração
    fun processCapturedColor(colorInt: Int) {
        val colorName = calculateColorName(colorInt)
        mineBlockFromColor(colorName)
    }

    private fun calculateColorName(colorInt: Int): String {
        val r = AndroidColor.red(colorInt)
        val g = AndroidColor.green(colorInt)
        val b = AndroidColor.blue(colorInt)

        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(r, g, b, hsv)

        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        if (value < 0.20) return "Preto"
        if (saturation < 0.15) return if (value > 0.85) "Branco" else "Cinzento"
        if (hue in 15f..60f && value < 0.70) return "Castanho"
        if (hue < 15f && value < 0.50 && saturation > 0.4) return "Castanho"

        return when {
            hue < 15f -> "Vermelho"
            hue < 45f -> "Laranja"
            hue < 75f -> "Amarelo"
            hue < 165f -> "Verde"
            hue < 260f -> "Azul"
            hue < 330f -> "Roxo"
            else -> "Vermelho"
        }
    }

    private fun mineBlockFromColor(colorCategory: String) {
        checkCooldownAndMine(2, { m -> "Limite atingido! Espera mais $m min." }) { userRef ->
            executeMiningColor(colorCategory, userRef)
        }
    }

    fun mineBlockFromStructure(iconResId: Int) {
        checkCooldownAndMine(10, { m -> "Estás cansado! Espera mais $m min." }) { userRef ->
            executeMiningStructure(iconResId, userRef)
        }
    }

    private fun checkCooldownAndMine(maxCount: Int, errorMsg: (Long) -> String, mineAction: (DatabaseReference) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
        val statusRef = userRef.child("miningStatus")

        statusRef.get().addOnSuccessListener { snapshot ->
            val now = System.currentTimeMillis()
            val windowStartTime = snapshot.child("windowStartTime").getValue(Long::class.java) ?: 0L
            val count = snapshot.child("count").getValue(Int::class.java) ?: 0
            val COOLDOWN_MS = 15 * 60 * 1000

            var newCount = count
            var newStartTime = windowStartTime
            var canMine = false

            if (now - windowStartTime > COOLDOWN_MS) {
                newCount = 1; newStartTime = now; canMine = true
            } else {
                if (count < maxCount) {
                    newCount = count + 1; canMine = true
                } else {
                    val minutesLeft = 15 - ((now - windowStartTime) / 60000)
                    _miningError.value = errorMsg(minutesLeft)
                    canMine = false
                }
            }
            if (canMine) {
                statusRef.updateChildren(mapOf("count" to newCount, "windowStartTime" to newStartTime))
                mineAction(userRef)
            }
        }
    }

    private fun executeMiningColor(colorCategory: String, userRef: DatabaseReference) {
        val (blockId, blockName, baseXp) = getBlockFromColor(colorCategory)
        processDrop(blockId, blockName, baseXp, userRef)
    }

    private fun executeMiningStructure(iconResId: Int, userRef: DatabaseReference) {
        val (blockId, blockName, baseXp) = getLootTableForIcon(iconResId)
        processDrop(blockId, blockName, baseXp, userRef)
    }

    private fun processDrop(blockId: String, blockName: String, baseXp: Int, userRef: DatabaseReference) {
        val pickaxeLevel = currentUserData?.pickaxeIndex ?: 0
        val quantity = Random.nextInt(1 + pickaxeLevel, 6 + (pickaxeLevel * 2))
        val totalXp = baseXp * quantity

        val result = MiningResult(blockId, blockName, quantity, totalXp, blockDrawable(blockId))

        userRef.get().addOnSuccessListener { snapshot ->
            val currentQty = snapshot.child("inventory").child(blockId).getValue(Int::class.java) ?: 0
            userRef.child("inventory").child(blockId).setValue(currentQty + quantity)

            val currentXP = snapshot.child("pontosXP").getValue(Int::class.java) ?: 0
            userRef.child("pontosXP").setValue(currentXP + totalXp)

            _miningResult.value = result
        }
    }


    // Loot Tables & Drawables

    fun clearMiningError() { _miningError.value = null }
    fun clearMiningResult() { _miningResult.value = null }

    private fun blockDrawable(id: String): Int {
        return when (id) {
            "diamond" -> R.drawable.bloco_diamante
            "emerald" -> R.drawable.bloco_esmeralda
            "gold" -> R.drawable.bloco_ouro
            "coal" -> R.drawable.bloco_carvao
            "iron" -> R.drawable.bloco_iron
            "stone" -> R.drawable.bloco_pedra
            "dirt" -> R.drawable.bloco_terra
            "grace" -> R.drawable.grace
            "wood" -> R.drawable.madeira
            "lapis" -> R.drawable.lapis
            "neder" -> R.drawable.netherite_b
            else -> R.drawable.bloco_terra
        }
    }

    private fun getBlockFromColor(color: String): Triple<String, String, Int> {
        val rand = Random.nextDouble()
        return when (color) {
            "Cinzento" -> if (rand < 0.70) Triple("stone", "Pedra", 1) else if (rand < 0.90) Triple("iron", "Ferro", 5) else Triple("coal", "Carvão", 3)
            "Castanho" -> if (rand < 0.60) Triple("dirt", "Terra", 1) else if (rand < 0.90) Triple("wood", "Madeira", 2) else Triple("gold", "Ouro", 10)
            "Verde" -> if (rand < 0.55) Triple("dirt", "Terra", 1) else if (rand < 0.90) Triple("grace", "Relva", 1) else Triple("emerald", "Esmeralda", 20)
            "Azul" -> if (rand < 0.80) Triple("lapis", "Lápis-lazúli", 5) else Triple("diamond", "Diamante", 50)
            "Amarelo" -> if (rand < 0.70) Triple("sand", "Areia", 1) else Triple("gold", "Minério de Ouro", 10)
            "Preto" -> if (rand < 0.50) Triple("coal", "Carvão", 3) else if (rand < 0.90) Triple("obsidian", "Obsidiana", 15) else Triple("neder", "Netherite", 100)
            else -> Triple("stone", "Pedra Misteriosa", 1)
        }
    }

    private fun getLootTableForIcon(iconResId: Int): Triple<String, String, Int> {
        val rand = Random.nextDouble()
        return when (iconResId) {
            R.drawable.castelo -> if (rand < 0.60) Triple("stone", "Pedra", 10) else if (rand < 0.90) Triple("iron", "Ferro", 25) else Triple("gold", "Ouro", 50)
            R.drawable.arvore -> if (rand < 0.70) Triple("wood", "Madeira", 5) else if (rand < 0.95) Triple("grace", "Relva", 5) else Triple("emerald", "Esmeralda", 40)
            R.drawable.calhao -> if (rand < 0.50) Triple("stone", "Pedra", 5) else if (rand < 0.90) Triple("coal", "Carvão", 15) else Triple("diamond", "Diamante", 100)
            R.drawable.casas, R.drawable.casass -> if (rand < 0.50) Triple("dirt", "Terra", 2) else if (rand < 0.80) Triple("wood", "Madeira", 8) else Triple("lapis", "Lápis-lazúli", 20)
            R.drawable.fogo -> if (rand < 0.60) Triple("coal", "Cinzas", 10) else if (rand < 0.95) Triple("gold", "Ouro", 30) else Triple("neder", "Netherite", 150)
            else -> Triple("stone", "Pedra Misteriosa", 5)
        }
    }
}