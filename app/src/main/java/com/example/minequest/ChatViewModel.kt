package com.example.minequest

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// --- DATA CLASS MESSAGE (Atualizada para Multi-Items) ---
data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val pickaxeIndex: Int = 0,
    val profileImageName: String = "steve",
    val isMine: Boolean = false,

    // Flags de Estado
    val isTrade: Boolean = false,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,

    // Mapas de Itens: "diamond" -> 5
    val offerItems: Map<String, Int> = emptyMap(),
    val requestItems: Map<String, Int> = emptyMap(),

    val targetId: String = "",
    val deliveryTimeMillis: Long = 0,
    val arrivalTimestamp: Long = 0,
    val xpReward: Int = 0
)

class ChatViewModel(private val context: Context) : ViewModel() {

    private val database = FirebaseDatabase.getInstance()
    private val chatRef = database.getReference("chat_global")
    private val usersRef = database.getReference("users")

    private val auth = FirebaseAuth.getInstance()
    private val myUserId = auth.currentUser?.uid ?: ""
    private var myUserName = "Player"
    private var myPickaxeIndex = 0
    private var myProfileImageName = "steve"

    // --- STATES ---
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    // O meu inventário (Lista simples de chaves para o seletor)
    private val _myInventory = MutableStateFlow<List<String>>(emptyList())
    val myInventory = _myInventory.asStateFlow()

    // Inventário do Alvo (Mapa com quantidades para validação)
    private val _targetInventory = MutableStateFlow<Map<String, Int>>(emptyMap())
    val targetInventory = _targetInventory.asStateFlow()

    init {
        fetchMyProfile()
        listenToMessages()
    }

    private fun fetchMyProfile() {
        if (myUserId.isEmpty()) return
        usersRef.child(myUserId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                myUserName = snapshot.child("username").getValue(String::class.java) ?: "Player"
                myPickaxeIndex = snapshot.child("pickaxeIndex").getValue(Int::class.java) ?: 0
                myProfileImageName = snapshot.child("profileImage").getValue(String::class.java) ?: "steve"
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // --- GESTÃO DE INVENTÁRIOS ---

    fun loadMyInventory() {
        if (myUserId.isEmpty()) return
        usersRef.child(myUserId).child("inventory").get().addOnSuccessListener { snapshot ->
            val owned = mutableListOf<String>()
            for (child in snapshot.children) {
                val qty = getIntSafe(child, "")
                if (qty > 0) owned.add(child.key ?: "")
            }
            _myInventory.value = owned
        }.addOnFailureListener { _myInventory.value = emptyList() }
    }

    fun loadTargetInventory(targetUserId: String?) {
        // Se for GLOBAL (targetUserId null), simulamos stock infinito de tudo
        if (targetUserId.isNullOrEmpty()) {
            val allBlocks = listOf("diamond", "gold", "iron", "coal", "emerald", "stone", "wood", "dirt", "grace", "neder", "lapis")
            // 9999 garante que a validação (qty > available) nunca falha no Global
            _targetInventory.value = allBlocks.associateWith { 9999 }
            return
        }

        // Se for PRIVADO, carregamos o inventário real com quantidades
        usersRef.child(targetUserId).child("inventory").get().addOnSuccessListener { snapshot ->
            val available = mutableMapOf<String, Int>()
            for (child in snapshot.children) {
                val qty = getIntSafe(child, "")
                if (qty > 0) available[child.key ?: ""] = qty
            }

            // Fallback para evitar erros se estiver vazio
            if (available.isEmpty()) available["dirt"] = 0

            _targetInventory.value = available
        }
    }

    // --- LÓGICA DE DISTÂNCIA E RECOMPENSA ---

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000 // Metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    private fun calculateDeliveryDetails(distanceMeters: Double): Pair<Long, Int> {
        val distanceKm = distanceMeters / 1000
        val thresholdKm = 1.0 // Podes ajustar este valor
        return if (distanceKm <= thresholdKm) {
            Pair(20 * 1000L, 5) // Perto: 20s, 5 XP
        } else {
            Pair(20 * 1000L, 15) // Longe: 20s, 15 XP
        }
    }


    // --- ENVIAR PROPOSTA (MULTI-ITEM) ---

    fun sendTradeProposal(
        offerItems: Map<String, Int>,
        requestItems: Map<String, Int>,
        targetUserId: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (offerItems.isEmpty() || requestItems.isEmpty()) {
            onError("You must offer and request at least one item.")
            return
        }

        // 1. Verificar se EU tenho stock suficiente para a minha oferta
        usersRef.child(myUserId).child("inventory").get().addOnSuccessListener { snapshot ->
            var hasAllItems = true

            for ((block, amount) in offerItems) {
                val myQuantity = snapshot.child(block).getValue(Int::class.java) ?: 0
                if (myQuantity < amount) {
                    hasAllItems = false
                    break
                }
            }

            if (hasAllItems) {
                // Se tiver items, calcula detalhes da entrega
                if (targetUserId != null) {
                    // TROCA PRIVADA: Calcula distância real
                    usersRef.child(myUserId).get().addOnSuccessListener { mySnap ->
                        usersRef.child(targetUserId).get().addOnSuccessListener { targetSnap ->
                            val myLat = mySnap.child("lat").getValue(Double::class.java) ?: 0.0
                            val myLng = mySnap.child("lng").getValue(Double::class.java) ?: 0.0
                            val tLat = targetSnap.child("lat").getValue(Double::class.java) ?: 0.0
                            val tLng = targetSnap.child("lng").getValue(Double::class.java) ?: 0.0

                            val dist = calculateDistance(myLat, myLng, tLat, tLng)
                            val (time, xp) = calculateDeliveryDetails(dist)


                         // adicionar a trade
                            pushTradeMessage(offerItems, requestItems, targetUserId,10 * 1000L , xp)
                            onSuccess()
                        }
                    }
                } else {
                    // TROCA GLOBAL: Tempo fixo (ex: 5 min)
                    pushTradeMessage(offerItems, requestItems, null, 5 * 60 * 1000L, 20)
                    onSuccess()
                }
            } else {
                onError("You don't have enough items for this offer!")
            }
        }
    }

    private fun pushTradeMessage(offerItems: Map<String, Int>, requestItems: Map<String, Int>, targetId: String?, time: Long, xp: Int) {
        val tradeMessageMap = hashMapOf(
            "senderId" to myUserId,
            "senderName" to myUserName,
            "text" to if (targetId != null) "Private Trade" else "Global Trade",
            "timestamp" to System.currentTimeMillis(),
            "pickaxeIndex" to myPickaxeIndex,
            "profileImage" to myProfileImageName,
            "isTrade" to true,
            "isCompleted" to false,
            "isCancelled" to false,
            "offerItems" to offerItems,     // Grava o mapa
            "requestItems" to requestItems, // Grava o mapa
            "targetId" to (targetId ?: ""),
            "deliveryTimeMillis" to time,
            "arrivalTimestamp" to 0L,
            "xpReward" to xp
        )
        chatRef.push().setValue(tradeMessageMap)
    }


    // --- ACEITAR TROCA (MULTI-ITEM) ---

    fun acceptTrade(message: Message, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (message.isCompleted || message.isCancelled) {
            onError("Trade is no longer valid.")
            return
        }

        // 1. Verificar se EU (quem aceita) tenho os itens pedidos (requestItems)
        usersRef.child(myUserId).child("inventory").get().addOnSuccessListener { myInvSnap ->
            var iHaveEverything = true
            for ((block, qty) in message.requestItems) {
                val current = myInvSnap.child(block).getValue(Int::class.java) ?: 0
                if (current < qty) iHaveEverything = false
            }

            if (iHaveEverything) {
                // 2. Verificar se o SENDER (quem criou) ainda tem os itens da oferta (offerItems)
                usersRef.child(message.senderId).child("inventory").get().addOnSuccessListener { senderInvSnap ->
                    var senderHasEverything = true
                    for ((block, qty) in message.offerItems) {
                        val current = senderInvSnap.child(block).getValue(Int::class.java) ?: 0
                        if (current < qty) senderHasEverything = false
                    }

                    if (senderHasEverything) {
                        // 3. Executar a troca (Subtrair inventários)

                        // Retirar de MIM
                        for ((block, qty) in message.requestItems) {
                            val current = myInvSnap.child(block).getValue(Int::class.java) ?: 0
                            usersRef.child(myUserId).child("inventory").child(block).setValue(current - qty)
                        }

                        // Retirar do SENDER
                        for ((block, qty) in message.offerItems) {
                            val current = senderInvSnap.child(block).getValue(Int::class.java) ?: 0
                            usersRef.child(message.senderId).child("inventory").child(block).setValue(current - qty)
                        }

                        // 4. Marcar hora de chegada
                        val arrivalTime = System.currentTimeMillis() + message.deliveryTimeMillis
                        chatRef.child(message.id).child("arrivalTimestamp").setValue(arrivalTime)

                        onSuccess()

                    } else {
                        onError("The other player no longer has the items!")
                    }
                }
            } else {
                onError("You don't have the required items!")
            }
        }
    }


    // --- FINALIZAR TROCA (DAR RECOMPENSAS) ---

    fun finalizeTrade(message: Message) {
        if (System.currentTimeMillis() < message.arrivalTimestamp) return

        // Eu recebo o que foi ofertado
        for ((block, qty) in message.offerItems) {
            addBlockToInventory(myUserId, block, qty)
        }

        // O sender recebe o que foi pedido
        for ((block, qty) in message.requestItems) {
            addBlockToInventory(message.senderId, block, qty)
        }

        // XP para ambos
        giveXp(myUserId, message.xpReward)
        giveXp(message.senderId, message.xpReward)

        chatRef.child(message.id).child("isCompleted").setValue(true)
    }


    // --- CANCELAR ---

    fun cancelTrade(message: Message) {
        // Só pode cancelar se ainda não foi aceite (arrivalTimestamp == 0)
        if (message.arrivalTimestamp == 0L) {
            chatRef.child(message.id).child("isCancelled").setValue(true)
        }
    }


    // --- MENSAGEM DE TEXTO ---

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val messageMap = hashMapOf(
            "senderId" to myUserId,
            "senderName" to myUserName,
            "text" to text,
            "timestamp" to System.currentTimeMillis(),
            "pickaxeIndex" to myPickaxeIndex,
            "profileImage" to myProfileImageName,
            "isTrade" to false,
            "targetId" to ""
        )
        chatRef.push().setValue(messageMap)
    }

    private fun listenToMessages() {
        chatRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val listaMensagens = mutableListOf<Message>()
                for (child in snapshot.children) {
                    val id = child.key ?: ""
                    val senderId = getStringSafe(child, "senderId")
                    val senderName = getStringSafe(child, "senderName").ifBlank { "Unknown" }
                    val text = getStringSafe(child, "text")
                    val timestamp = try { child.child("timestamp").getValue(Long::class.java) ?: 0L } catch (e: Exception) { 0L }
                    val pIndex = getIntSafe(child, "pickaxeIndex")
                    val pImageName = getStringSafe(child, "profileImage").ifBlank { "steve" }

                    val isTrade = child.child("isTrade").getValue(Boolean::class.java) ?: false
                    val isCompleted = child.child("isCompleted").getValue(Boolean::class.java) ?: false
                    val isCancelled = child.child("isCancelled").getValue(Boolean::class.java) ?: false

                    // --- PARSING DOS MAPAS ---
                    val offerItemsMap = mutableMapOf<String, Int>()
                    child.child("offerItems").children.forEach { item ->
                        val qty = item.getValue(Int::class.java) ?: item.getValue(Long::class.java)?.toInt() ?: 0
                        offerItemsMap[item.key!!] = qty
                    }

                    val requestItemsMap = mutableMapOf<String, Int>()
                    child.child("requestItems").children.forEach { item ->
                        val qty = item.getValue(Int::class.java) ?: item.getValue(Long::class.java)?.toInt() ?: 0
                        requestItemsMap[item.key!!] = qty
                    }
                    // -------------------------

                    val targetId = getStringSafe(child, "targetId")
                    val deliveryTime = try { child.child("deliveryTimeMillis").getValue(Long::class.java) ?: 0L } catch(e:Exception){0L}
                    val arrivalTime = try { child.child("arrivalTimestamp").getValue(Long::class.java) ?: 0L } catch(e:Exception){0L}
                    val xpReward = getIntSafe(child, "xpReward")

                    val msg = Message(
                        id = id,
                        senderId = senderId,
                        senderName = senderName,
                        text = text,
                        timestamp = timestamp,
                        pickaxeIndex = pIndex,
                        profileImageName = pImageName,
                        isMine = (senderId == myUserId),
                        isTrade = isTrade,
                        isCompleted = isCompleted,
                        isCancelled = isCancelled,
                        offerItems = offerItemsMap,
                        requestItems = requestItemsMap,
                        targetId = targetId,
                        deliveryTimeMillis = deliveryTime,
                        arrivalTimestamp = arrivalTime,
                        xpReward = xpReward
                    )
                    listaMensagens.add(msg)
                }
                _messages.value = listaMensagens
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // --- Helpers Genéricos ---

    private fun addBlockToInventory(userId: String, block: String, amount: Int) {
        val ref = usersRef.child(userId).child("inventory").child(block)
        ref.get().addOnSuccessListener {
            val current = it.getValue(Int::class.java) ?: 0
            ref.setValue(current + amount)
        }
    }

    private fun giveXp(userId: String, amount: Int) {
        val ref = usersRef.child(userId).child("pontosXP")
        ref.get().addOnSuccessListener {
            val current = it.getValue(Int::class.java) ?: 0
            ref.setValue(current + amount)
        }
    }

    private fun getStringSafe(snapshot: DataSnapshot, key: String): String {
        return snapshot.child(key).getValue(String::class.java) ?: ""
    }

    private fun getIntSafe(snapshot: DataSnapshot, key: String): Int {
        val child = if (key.isEmpty()) snapshot else snapshot.child(key)
        return try {
            child.getValue(Int::class.java)
                ?: child.getValue(Long::class.java)?.toInt()
                ?: 0
        } catch (e: Exception) { 0 }
    }
}

class ChatViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}