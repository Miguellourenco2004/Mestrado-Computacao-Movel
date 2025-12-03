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
import kotlin.random.Random


data class MiningResult(
    val blockId: String,
    val blockName: String,
    val quantity: Int,
    val xpEarned: Int,
    val imageRes: Int
)

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

   // Logica blocos
    private val _miningResult = MutableStateFlow<MiningResult?>(null)
    val miningResult = _miningResult.asStateFlow()

    private var currentUserData: User? = null


    private val _miningError = MutableStateFlow<String?>(null)
    val miningError = _miningError.asStateFlow()

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
                        "&mode=walking"+
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





    init {
        // Carregar dados do user atual para ter acesso à picareta
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



    fun mineBlockFromColor(colorCategory: String) {
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

                newCount = 1
                newStartTime = now
                canMine = true
            } else {

                if (count < 2) {

                    newCount = count + 1
                    canMine = true
                } else {

                    val minutesLeft = 15 - ((now - windowStartTime) / 60000)
                    _miningError.value = "Limite atingido! Espera mais $minutesLeft min até poderes voltar a trabalhar."
                    canMine = false
                }
            }

            if (canMine) {

                val updates = mapOf(
                    "count" to newCount,
                    "windowStartTime" to newStartTime
                )
                statusRef.updateChildren(updates)


                executeMining(colorCategory, userRef)
            }
        }
    }

    private fun executeMining(colorCategory: String, userRef: com.google.firebase.database.DatabaseReference) {

        val (blockId, blockName, baseXp) = getBlockFromColor(colorCategory)


        val pickaxeLevel = currentUserData?.pickaxeIndex ?: 0
        val minQty = 1 + pickaxeLevel
        val maxQty = 5 + (pickaxeLevel * 2)
        val quantity = Random.nextInt(minQty, maxQty + 1)
        val totalXp = baseXp * quantity

        val result = MiningResult(
            blockId = blockId,
            blockName = blockName,
            quantity = quantity,
            xpEarned = totalXp,
            imageRes = blockDrawable(blockId)
        )


        userRef.get().addOnSuccessListener { snapshot ->
            val currentQty = snapshot.child("inventory").child(blockId).getValue(Int::class.java) ?: 0
            userRef.child("inventory").child(blockId).setValue(currentQty + quantity)

            val currentXP = snapshot.child("pontosXP").getValue(Int::class.java) ?: 0
            userRef.child("pontosXP").setValue(currentXP + totalXp)

            _miningResult.value = result
        }
    }

    fun clearMiningError() {
        _miningError.value = null
    }
    fun clearMiningResult() {
        _miningResult.value = null
    }


    fun getBlockFromColor(color : String): Triple<String, String, Int> {

        val rand = Random.nextDouble()


        return when (color){
            "Cinzento" -> {
                // 70% Pedra, 20% Ferro, 10% Carvão
                when {
                    rand < 0.70 -> Triple("stone", "Pedra", 1)
                    rand < 0.90 -> Triple("iron", "Ferro", 5)
                    else -> Triple("coal", "Carvão", 3)
                }
            }
            "Castanho" -> {
                // 60% Terra, 30% Madeira, 10% Ouro (raro na terra!)
                when {
                    rand < 0.60 -> Triple("dirt", "Terra", 1)
                    rand < 0.90 -> Triple("wood", "Madeira", 2)
                    else -> Triple("gold", "Ouro", 10)
                }
            }
            "Verde" -> {
                // 80% Terra, 20% Esmeralda
                when {
                    rand < 0.55 -> Triple("dirt", "Terra", 1)
                    rand < 0.90  -> Triple("grace", "Relva", 1)
                    else -> Triple("emerald", "Esmeralda", 20)
                }
            }

            "Azul" -> {
                // 80% Lapis, 20% Diamante
                when {
                    rand < 0.80 -> Triple("lapis", "Lápis-lazúli", 5)
                    else -> Triple("diamond", "Diamante", 50) // Muito XP!
                }
            }
            "Amarelo" -> {
                // 70% Areia, 30% Ouro
                when {
                    rand < 0.70 -> Triple("sand", "Areia", 1)
                    else -> Triple("gold", "Minério de Ouro", 10)
                }
            }
            "Preto" -> {
                // 50% Carvão, 40% Obsidian, 10% Netherite (Muito Raro)
                when {
                    rand < 0.50 -> Triple("coal", "Carvão", 3)
                    rand < 0.90 -> Triple("obsidian", "Obsidiana", 15)
                    else -> Triple("neder", "Netherite ", 100)
                }
            }
            else -> Triple("stone", "Pedra Misteriosa", 1)
        }


    }



    private fun blockDrawable(id: String): Int {
        return when (id) {
            "diamond" -> R.drawable.bloco_diamante
            "emerald" -> R.drawable.bloco_esmeralda
            "gold" -> R.drawable.bloco_ouro
            "coal" -> R.drawable.bloco_carvao
            "iron" -> R.drawable.iron
            "stone" -> R.drawable.bloco_pedra
            "dirt" -> R.drawable.bloco_terra
            "grace" -> R.drawable.grace
            "wood" -> R.drawable.madeira
            "lapis" -> R.drawable.lapis
            "neder" -> R.drawable.netherite_b
            else -> R.drawable.bloco_terra
        }
    }






}
