package com.example.minequest

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minequest.model.User
// --- NOVOS IMPORTS PARA AS MISSÕES ---
import com.example.minequest.model.QuestType
import com.example.minequest.model.UserQuestProgress
import kotlinx.coroutines.tasks.await
import java.util.Calendar
// -------------------------------------
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

// Mantemos o resultado simples, como pediste
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

    // Sistema de Mineração
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
                    user.id = snap.key ?: ""
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


    // NAVEGAÇÃO

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

        if (value < 0.20) return "Black"
        if (saturation < 0.15) return if (value > 0.85) "White" else "Gray"
        if (hue in 15f..60f && value < 0.70) return "Brown"
        if (hue < 15f && value < 0.50 && saturation > 0.4) return "Brown"

        return when {
            hue < 15f -> "Red"
            hue < 45f -> "Orange"
            hue < 75f -> "Yellow"
            hue < 165f -> "Green"
            hue < 260f -> "Blue"
            hue < 330f -> "Purple"
            else -> "Red"
        }
    }

    private fun mineBlockFromColor(colorCategory: String) {
        checkCooldownAndMine(2, { m -> "Limit reached! Wait $m more min." }) { userRef ->
            executeMiningColor(colorCategory, userRef)
        }
    }

    fun mineBlockFromStructure(iconResId: Int) {
        checkCooldownAndMine(10, { m -> "You are tired! Wait $m more min." }) { userRef ->
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

    // --- FUNÇÃO AUXILIAR PARA VERIFICAR A DATA DA MISSÃO ---
    private fun isQuestAssignedToday(assignedDate: Long): Boolean {
        if (assignedDate == 0L) return false
        val assignedCal = Calendar.getInstance().apply { timeInMillis = assignedDate }
        val todayCal = Calendar.getInstance()
        return assignedCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                assignedCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    }

    // --- FUNÇÃO PROCESSDROP ATUALIZADA (COM LÓGICA DE MISSÕES) ---
    private fun processDrop(blockId: String, blockName: String, baseXp: Int, userRef: DatabaseReference) {
        viewModelScope.launch {
            try {
                // 1. Cálculos iniciais da mineração
                val pickaxeLevel = currentUserData?.pickaxeIndex ?: 0
                val quantity = Random.nextInt(1 + pickaxeLevel, 6 + (pickaxeLevel * 2))
                var totalXp = baseXp * quantity

                // 2. Lógica de Missões (Silenciosa)
                // Verifica se há alguma missão de MINERAR e atualiza o progresso
                val questsRef = userRef.child("quest_progress")

                // 'await()' permite ler os dados de forma síncrona dentro da coroutine
                val questsSnapshot = questsRef.get().await()

                for (child in questsSnapshot.children) {
                    val progress = child.getValue(UserQuestProgress::class.java) ?: continue

                    // Verifica: Missão não completa + Tipo MINE_BLOCKS + Atribuída hoje
                    if (!progress.isCompleted &&
                        progress.questDetails?.type == QuestType.MINE_BLOCKS &&
                        isQuestAssignedToday(progress.assignedDate)) {

                        val target = progress.questDetails.target
                        val newProgressValue = (progress.currentProgress + quantity).coerceAtMost(target)
                        var isCompletedNow = false

                        // Se atingiu o objetivo
                        if (newProgressValue >= target) {
                            isCompletedNow = true
                            // Adiciona a recompensa da missão ao XP total desta mineração
                            totalXp += progress.questDetails.reward
                        }

                        // Atualiza o progresso no Firebase
                        // O Ranking "ouve" isto e vai atualizar a barra automaticamente!
                        val updates = mapOf(
                            "currentProgress" to newProgressValue,
                            "isCompleted" to isCompletedNow
                        )
                        child.ref.updateChildren(updates).await()
                    }
                }

                // 3. Atualizar Inventário e XP do User (com o total já somado)
                val snapshot = userRef.get().await()
                val currentQty = snapshot.child("inventory").child(blockId).getValue(Int::class.java) ?: 0
                val currentXP = snapshot.child("pontosXP").getValue(Int::class.java) ?: 0

                userRef.child("inventory").child(blockId).setValue(currentQty + quantity).await()
                userRef.child("pontosXP").setValue(currentXP + totalXp).await()

                // 4. Mostrar resultado (sem mensagem de missão, apenas o XP total)
                _miningResult.value = MiningResult(blockId, blockName, quantity, totalXp, blockDrawable(blockId))

            } catch (e: Exception) {
                e.printStackTrace()
                _miningError.value = "Error processing mining."
            }
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
            "wood" -> R.drawable.wood
            "lapis" -> R.drawable.lapis
            "neder" -> R.drawable.netherite_b
            else -> R.drawable.bloco_terra
        }
    }

    private fun getBlockFromColor(color: String): Triple<String, String, Int> {
        val rand = Random.nextDouble()
        return when (color) {
            "Gray" -> if (rand < 0.70) Triple("stone", "Stone", 1) else if (rand < 0.90) Triple("iron", "Iron", 5) else Triple("coal", "Coal", 3)
            "Brown" -> if (rand < 0.60) Triple("dirt", "Dirt", 1) else if (rand < 0.90) Triple("wood", "Wood", 2) else Triple("gold", "Gold", 10)
            "Green" -> if (rand < 0.55) Triple("dirt", "Dirt", 1) else if (rand < 0.90) Triple("grace", "Grass", 1) else Triple("emerald", "Emerald", 20)
            "Blue" -> if (rand < 0.80) Triple("lapis", "Lapis Lazuli", 5) else Triple("diamond", "Diamond", 50)
            "Yellow" -> if (rand < 0.70) Triple("sand", "Sand", 1) else Triple("gold", "Gold Ore", 10)
            "Black" -> if (rand < 0.50) Triple("coal", "Coal", 3) else if (rand < 0.90) Triple("obsidian", "Obsidian", 15) else Triple("neder", "Netherite", 100)
            else -> Triple("stone", "Mysterious Stone", 1)
        }
    }

    private fun getLootTableForIcon(iconResId: Int): Triple<String, String, Int> {
        val rand = Random.nextDouble()
        return when (iconResId) {
            R.drawable.castelo -> if (rand < 0.60) Triple("stone", "Stone", 10) else if (rand < 0.90) Triple("iron", "Iron", 25) else Triple("gold", "Gold", 50)
            R.drawable.arvore -> if (rand < 0.70) Triple("wood", "Wood", 5) else if (rand < 0.95) Triple("grace", "Grass", 5) else Triple("emerald", "Emerald", 40)
            R.drawable.calhao -> if (rand < 0.50) Triple("stone", "Stone", 5) else if (rand < 0.90) Triple("coal", "Coal", 15) else Triple("diamond", "Diamond", 100)
            R.drawable.casas, R.drawable.casass -> if (rand < 0.50) Triple("dirt", "Dirt", 2) else if (rand < 0.80) Triple("wood", "Wood", 8) else Triple("lapis", "Lapis Lazuli", 20)
            R.drawable.fogo -> if (rand < 0.60) Triple("coal", "Coal", 10) else if (rand < 0.95) Triple("gold", "Gold", 30) else Triple("neder", "Netherite", 150)
            R.drawable.coiso -> if (rand < 0.40) Triple("coal", "Coal", 10) else if (rand < 0.95) Triple("gold", "Gold", 30) else Triple("neder", "Netherite", 150)
            else -> Triple("stone", "Mysterious Stone", 5)
        }
    }
}