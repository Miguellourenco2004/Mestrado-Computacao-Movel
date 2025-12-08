package com.example.minequest

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.minequest.ui.theme.MineQuestFont
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// --- CORES DO TEMA ---
val ChatBackground = Color(0xFF52A435)
val MyBubbleColor = Color(0xFF52A435)
val OtherBubbleColor = Color(0xFF323232)
val BorderColor = Color(0xFF6B3B25)

// --- MODELO DO INVENTÁRIO (Para o Chat) ---
data class ChatInventorySlot(
    val blockId: String,
    val quantity: Int
)

// --- UTILS: Lógica das Picaretas ---
fun getPickaxeImage(index: Int): Int {
    return when (index) {
        0 -> R.drawable.madeira
        1 -> R.drawable.pedra
        2 -> R.drawable.ferro
        3 -> R.drawable.ouro
        4 -> R.drawable.diamante
        5 -> R.drawable.netherite
        else -> R.drawable.madeira
    }
}

fun getBlockDrawableChat(id: String): Int {
    return when (id) {
        "diamond" -> R.drawable.bloco_diamante
        "emerald" -> R.drawable.bloco_esmeralda
        "gold" -> R.drawable.bloco_ouro
        "coal" -> R.drawable.bloco_carvao
        else -> R.drawable.bloco_terra
    }
}

fun getProfileDrawable(name: String): Int {
    return when (name) {
        "fb9edad1e26f75" -> R.drawable._fb9edad1e26f75
        "4efed46e89c72955ddc7c77ad08b2ee" -> R.drawable._4efed46e89c72955ddc7c77ad08b2ee
        "578bfd439ef6ee41e103ae82b561986" -> R.drawable._578bfd439ef6ee41e103ae82b561986
        "faf3182a063a0f2a825cb39d959bae7" -> R.drawable._faf3182a063a0f2a825cb39d959bae7
        "a9a4ec03fa9afc407028ca40c20ed774" -> R.drawable.a9a4ec03fa9afc407028ca40c20ed774
        "big_villager_face" -> R.drawable.big_villager_face
        "images" -> R.drawable.images
        "steve" -> R.drawable.steve
        else -> R.drawable.minecraft_creeper_face
    }
}

// --- UTILS: Formatar Hora ---
fun formatTime(timestamp: Long): String {
    val now = Date()
    val msgDate = Date(timestamp)
    val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val isToday = dayFormat.format(now) == dayFormat.format(msgDate)

    return if (isToday) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(msgDate)
    } else {
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        sdf.format(msgDate)
    }
}

// --- MODELO DE DADOS ---
data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val pickaxeIndex: Int = 0,
    val profileImageName: String = "steve",
    val isMine: Boolean = false
)

// --- VIEWMODEL ---
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

    init {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            myUserId = currentUser.uid

            usersRef.child(myUserId).get().addOnSuccessListener { snapshot ->
                myUserName = getStringSafe(snapshot, "username").ifBlank { "Mineiro" }
                myPickaxeIndex = getIntSafe(snapshot, "pickaxeIndex")
                myProfileImageName = getStringSafe(snapshot, "profileImage").ifBlank { "steve" }

            }.addOnFailureListener {
                myUserName = "Mineiro_Erro"
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

                    val msg = Message(
                        id = id,
                        senderId = senderId,
                        senderName = senderName,
                        text = text,
                        timestamp = timestamp,
                        pickaxeIndex = pIndex,
                        profileImageName = pImageName,
                        isMine = (senderId == myUserId)
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
            "profileImage" to myProfileImageName
        )

        chatRef.push().setValue(newMessageMap)
    }
}

// --- FACTORY ---
class ChatViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- ECRÃ CHAT ---
@Composable
fun Chat(navController: NavHostController) {

    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(context))

    val messages by viewModel.messages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var selectedUserMessage by remember { mutableStateOf<Message?>(null) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (selectedUserMessage != null) {
        UserProfileDialog(
            message = selectedUserMessage!!,
            onDismiss = { selectedUserMessage = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatBackground)
            .padding(16.dp)
            .imePadding()
    ) {

        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = "Global Chat",
                fontFamily = MineQuestFont,
                fontSize = 28.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .background(Color(0x80000000), RectangleShape)
                .border(2.dp, BorderColor,  RectangleShape)
                .padding(8.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(
                        message = msg,
                        onHeaderClick = { selectedUserMessage = msg }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontFamily = MineQuestFont,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .background(OtherBubbleColor,  RectangleShape)
                    .border(2.dp, BorderColor,  RectangleShape)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                shape =  RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MyBubbleColor),
                modifier = Modifier
                    .size(50.dp)
                    .border(2.dp, BorderColor,  RectangleShape),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Send, "Send", tint = Color.White)
            }
        }
    }
}

// --- DIALOG DO PERFIL COM INVENTÁRIO ---
@Composable
fun UserProfileDialog(message: Message, onDismiss: () -> Unit) {
    val database = FirebaseDatabase.getInstance().getReference("users")
    var inventorySlots by remember { mutableStateOf<List<ChatInventorySlot>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(message.senderId) {
        if (message.senderId.isNotEmpty()) {
            database.child(message.senderId).child("inventory").get()
                .addOnSuccessListener { snapshot ->
                    val slots = mutableListOf<ChatInventorySlot>()
                    for (item in snapshot.children) {
                        val blockId = item.key ?: continue
                        val quantity = item.getValue(Int::class.java) ?: 0
                        slots += splitIntoSlotsChat(blockId, quantity)
                    }
                    inventorySlots = slots
                    isLoading = false
                }
                .addOnFailureListener { isLoading = false }
        } else {
            isLoading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .background(MineDarkGreen,  RectangleShape)
                .border(3.dp, BorderColor,  RectangleShape)
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Player Profile",
                    fontFamily = MineQuestFont,
                    fontSize = 24.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Image(
                    painter = painterResource(id = getProfileDrawable(message.profileImageName)),
                    contentDescription = "Large Avatar",
                    modifier = Modifier
                        .size(100.dp)
                        .border(2.dp, BorderColor,  RectangleShape)
                        .background(Color(0xFF323232),  RectangleShape)
                        .padding(8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = message.senderName,
                    fontFamily = MineQuestFont,
                    fontSize = 28.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = getPickaxeImage(message.pickaxeIndex)),
                        contentDescription = "Pickaxe",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Level ${message.pickaxeIndex + 1}",
                        fontFamily = MineQuestFont,
                        fontSize = 16.sp,
                        color = Color.Yellow
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Inventory",
                    fontFamily = MineQuestFont,
                    fontSize = 18.sp,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    CircularProgressIndicator(color = Color.White)
                } else if (inventorySlots.isEmpty()) {
                    Text("Empty Inventory", color = Color.Gray, fontFamily = MineQuestFont)
                } else {
                    // AQUI CHAMAMOS A GRELHA SEM LIMITES
                    ChatInventoryGrid(slots = inventorySlots, columns = 4)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = BorderColor),
                    shape =  RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Close", fontFamily = MineQuestFont, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ChatInventoryGrid(slots: List<ChatInventorySlot>, columns: Int = 4) {
    val rowsNeeded = (slots.size + columns - 1) / columns
    val rows = maxOf(rowsNeeded, 3)

    val totalSlots = rows * columns
    val filledSlots = slots + List((totalSlots - slots.size).coerceAtLeast(0)) { null }

    Column(
        modifier = Modifier
            .background(Color(0xFFC6C6C6))
            .border(2.dp, Color.Black)
            .padding(4.dp)
    ) {
        for (row in 0 until rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index < filledSlots.size) {
                        ChatInventorySlotView(filledSlots[index], modifier = Modifier.weight(1f))
                    } else {
                        ChatInventorySlotView(null, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (row < rows - 1) Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
fun ChatInventorySlotView(slot: ChatInventorySlot?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(Color(0xFF8B8B8B))
            .border(1.dp, Color(0xFF373737))
            .padding(1.dp)
    ) {
        if (slot != null) {
            Image(
                painter = painterResource(id = getBlockDrawableChat(slot.blockId)),
                contentDescription = slot.blockId,
                modifier = Modifier.fillMaxSize()
            )
            if (slot.quantity > 1) {
                Text(
                    text = "${slot.quantity}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = MineQuestFont,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}

fun splitIntoSlotsChat(blockId: String, quantity: Int): List<ChatInventorySlot> {
    val slots = mutableListOf<ChatInventorySlot>()
    var remaining = quantity
    while (remaining > 0) {
        val slotQty = minOf(64, remaining)
        slots.add(ChatInventorySlot(blockId, slotQty))
        remaining -= slotQty
    }
    return slots
}

@Composable
fun ChatBubble(message: Message, onHeaderClick: () -> Unit) {
    val bubbleColor = if (message.isMine) MyBubbleColor else OtherBubbleColor
    val alignment = if (message.isMine) Alignment.End else Alignment.Start
    val headerArrangement = if (message.isMine) Arrangement.End else Arrangement.Start

    val bubbleShape = if (message.isMine) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = headerArrangement,
            modifier = Modifier
                .padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
                .fillMaxWidth()
                .clickable { onHeaderClick() }
        ) {
            Image(
                painter = painterResource(id = getProfileDrawable(message.profileImageName)),
                contentDescription = "Avatar",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Image(
                painter = painterResource(id = getPickaxeImage(message.pickaxeIndex)),
                contentDescription = "Pickaxe",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = message.senderName,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MineQuestFont
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatTime(message.timestamp),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontFamily = MineQuestFont
            )
        }

        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                color = Color.White,
                fontFamily = MineQuestFont,
                fontSize = 16.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}