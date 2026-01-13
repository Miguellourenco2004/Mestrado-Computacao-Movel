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
import java.util.UUID

data class ChatInventorySlot(
    val blockId: String,
    val quantity: Int
)

data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val pickaxeIndex: Int = 0,
    val profileImageName: String = "steve",
    val isMine: Boolean = false,
    val isTrade: Boolean = false,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val offerBlock: String = "",
    val offerAmount: Int = 0,
    val requestBlock: String = "",
    val requestAmount: Int = 0,
    val targetId: String = ""
)

class ChatViewModel(private val context: Context) : ViewModel() {

    private val database = FirebaseDatabase.getInstance()
    private val chatRef = database.getReference("chat_global")
    private val usersRef = database.getReference("users")
    private val auth = FirebaseAuth.getInstance()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private var myUserId: String = ""
    private var myUserName: String = "Anonimo"
    private var myPickaxeIndex: Int = 0
    private var myProfileImageName: String = "steve"


    private val _targetInventory = MutableStateFlow<List<String>>(emptyList())
    val targetInventory = _targetInventory.asStateFlow()

    fun loadTargetInventory(targetUserId: String?) {

        if (targetUserId.isNullOrEmpty()) {

            _targetInventory.value = listOf("diamond", "gold", "iron", "coal", "emerald", "stone", "wood", "dirt", "grace", "neder", "lapis")
            return
        }


        // Se for PRIVADO, vamos ao Firebase ver o que ele tem
        usersRef.child(targetUserId).child("inventory").get().addOnSuccessListener { snapshot ->
            val available = mutableListOf<String>()
            for (child in snapshot.children) {
                val qty = child.getValue(Int::class.java) ?: 0
                if (qty > 0) {
                    available.add(child.key ?: "")
                }
            }

            if (available.isEmpty()) available.add("dirt")
            _targetInventory.value = available
        }
    }


    private val _myInventory = MutableStateFlow<List<String>>(emptyList())
    val myInventory = _myInventory.asStateFlow()

    fun loadMyInventory() {
        if (myUserId.isEmpty()) return

        usersRef.child(myUserId).child("inventory").get().addOnSuccessListener { snapshot ->
            val owned = mutableListOf<String>()
            for (child in snapshot.children) {
                // Tenta ler como Int, se falhar tenta Long, se falhar assume 0
                val qty = try {
                    child.getValue(Int::class.java)
                        ?: child.getValue(Long::class.java)?.toInt()
                        ?: 0
                } catch (e: Exception) {
                    0
                }

                if (qty > 0) {
                    owned.add(child.key ?: "")
                }
            }
            _myInventory.value = owned
        }.addOnFailureListener {
            // Se falhar, fica vazio
            _myInventory.value = emptyList()
        }
    }



    init {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            myUserId = currentUser.uid

            usersRef.child(myUserId).get().addOnSuccessListener { snapshot ->
                myUserName = getStringSafe(snapshot, "username").ifBlank { "Mineiro" }
                myPickaxeIndex = getIntSafe(snapshot, "pickaxeIndex")
                myProfileImageName = getStringSafe(snapshot, "profileImage").ifBlank { "steve" }
            }
        } else {
            myUserId = UUID.randomUUID().toString()
            myUserName = "Visitante"
        }

        listenToMessages()
    }

    private fun getIntSafe(snapshot: DataSnapshot, field: String): Int {
        val value = snapshot.child(field).value
        return when (value) {
            is Long -> value.toInt()
            is Int -> value
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
    }

    private fun getStringSafe(snapshot: DataSnapshot, field: String): String {
        val value = snapshot.child(field).value
        return value?.toString() ?: ""
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

                    // Trocas
                    val isTrade = child.child("isTrade").getValue(Boolean::class.java) ?: false
                    val isCompleted = child.child("isCompleted").getValue(Boolean::class.java) ?: false
                    val isCancelled = child.child("isCancelled").getValue(Boolean::class.java) ?: false // <--- LER DO FIREBASE

                    val offerBlock = getStringSafe(child, "offerBlock")
                    val offerAmount = getIntSafe(child, "offerAmount")
                    val requestBlock = getStringSafe(child, "requestBlock")
                    val requestAmount = getIntSafe(child, "requestAmount")

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
                        isCancelled = isCancelled, // <--- PASSAR PARA A MENSAGEM
                        offerBlock = offerBlock,
                        offerAmount = offerAmount,
                        requestBlock = requestBlock,
                        requestAmount = requestAmount
                    )
                    listaMensagens.add(msg)
                }
                _messages.value = listaMensagens
            }

            override fun onCancelled(error: DatabaseError) { }
        })
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val newMessageMap = hashMapOf(
            "senderId" to myUserId,
            "senderName" to myUserName,
            "text" to text,
            "timestamp" to System.currentTimeMillis(),
            "pickaxeIndex" to myPickaxeIndex,
            "profileImage" to myProfileImageName,
            "isTrade" to false
        )

        chatRef.push().setValue(newMessageMap)
    }

    fun sendTradeProposal(
        offerBlock: String,
        offerAmount: Int,
        requestBlock: String,
        requestAmount: Int,
        targetUserId: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (offerAmount <= 0 || requestAmount <= 0) {
            onError("Amount must be greater than 0")
            return
        }

        usersRef.child(myUserId).child("inventory").child(offerBlock).get()
            .addOnSuccessListener { snapshot ->
                val myQuantity = snapshot.getValue(Int::class.java) ?: 0

                if (myQuantity >= offerAmount) {
                    val tradeMessageMap = hashMapOf(
                        "senderId" to myUserId,
                        "senderName" to myUserName,
                        "text" to "Trade Offer!",
                        "timestamp" to System.currentTimeMillis(),
                        "pickaxeIndex" to myPickaxeIndex,
                        "profileImage" to myProfileImageName,
                        "isTrade" to true,
                        "isCompleted" to false,
                        "isCancelled" to false, // Começa como falso
                        "offerBlock" to offerBlock,
                        "offerAmount" to offerAmount,
                        "requestBlock" to requestBlock,
                        "requestAmount" to requestAmount,
                        "targetId" to (targetUserId ?: "")
                    )

                    chatRef.push().setValue(tradeMessageMap)
                    onSuccess()
                } else {
                    onError("You don't have enough $offerBlock! You have $myQuantity.")
                }
            }
            .addOnFailureListener {
                onError("Failed to check inventory.")
            }
    }

    fun cancelTrade(message: Message) {
        if (message.isMine && !message.isCompleted) {
            chatRef.child(message.id).child("isCancelled").setValue(true)
        }
    }

    fun acceptTrade(message: Message, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (message.isCancelled) {
            onError("This trade was cancelled!")
            return
        }

        if (message.senderId == myUserId) {
            onError("You cannot trade with yourself!")
            return
        }

        val senderInvRef = usersRef.child(message.senderId).child("inventory")
        val myInvRef = usersRef.child(myUserId).child("inventory")
        val msgRef = chatRef.child(message.id)

        myInvRef.child(message.requestBlock).get().addOnSuccessListener { mySnapshot ->
            val myHas = mySnapshot.getValue(Int::class.java) ?: 0

            if (myHas < message.requestAmount) {
                onError("You don't have enough ${message.requestBlock}!")
                return@addOnSuccessListener
            }

            senderInvRef.child(message.offerBlock).get().addOnSuccessListener { senderSnapshot ->
                val senderHas = senderSnapshot.getValue(Int::class.java) ?: 0

                if (senderHas < message.offerAmount) {
                    onError("The other player no longer has the items!")
                    return@addOnSuccessListener
                }

                senderInvRef.child(message.offerBlock).setValue(senderHas - message.offerAmount)

                senderInvRef.child(message.requestBlock).get().addOnSuccessListener { sReqSnap ->
                    val current = sReqSnap.getValue(Int::class.java) ?: 0
                    senderInvRef.child(message.requestBlock).setValue(current + message.requestAmount)
                }

                myInvRef.child(message.requestBlock).setValue(myHas - message.requestAmount)

                myInvRef.child(message.offerBlock).get().addOnSuccessListener { mOffSnap ->
                    val current = mOffSnap.getValue(Int::class.java) ?: 0
                    myInvRef.child(message.offerBlock).setValue(current + message.offerAmount)
                }

                msgRef.child("isCompleted").setValue(true)

                onSuccess()
            }
        }
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