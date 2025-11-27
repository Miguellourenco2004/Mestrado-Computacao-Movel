package com.example.minequest

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

//Lógica das Picaretas
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

//Lógica das Imagens de Perfil
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

//  Formatar Hora Inteligente
fun formatTime(timestamp: Long): String {
    val now = Date()
    val msgDate = Date(timestamp)

    // Formatadores
    val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    // Verifica se a mensagem é de hoje
    val isToday = dayFormat.format(now) == dayFormat.format(msgDate)

    return if (isToday) {
        // Se for hoje, mostra só as horas
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(msgDate)
    } else {
        // Se for outro dia, mostra dia/mês e horas
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
                // Usa getStringSafe para evitar erros se o nome for um número por engano
                myUserName = getStringSafe(snapshot, "username").ifBlank { "Mineiro" }

                myPickaxeIndex = getIntSafe(snapshot, "pickaxeIndex")

                // Usa getStringSafe aqui também! Se for número, vira texto e não cracha.
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

    // --- FUNÇÃO SEGURA PARA LER NÚMEROS ---
    private fun getIntSafe(snapshot: DataSnapshot, field: String): Int {
        val value = snapshot.child(field).value
        return when (value) {
            is Long -> value.toInt()
            is Int -> value
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
    }

    // --- NOVA FUNÇÃO SEGURA PARA LER TEXTO ---
    // Isto resolve o erro "Failed to convert Long to String"
    private fun getStringSafe(snapshot: DataSnapshot, field: String): String {
        val value = snapshot.child(field).value
        return value?.toString() ?: "" // Se for número, converte para string. Se for null, devolve vazio.
    }

    private fun listenToMessages() {
        chatRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val listaMensagens = mutableListOf<Message>()

                for (child in snapshot.children) {
                    val id = child.key ?: ""

                    // APLICAÇÃO GERAL DA LEITURA SEGURA
                    val senderId = getStringSafe(child, "senderId")
                    val senderName = getStringSafe(child, "senderName").ifBlank { "Unknown" }
                    val text = getStringSafe(child, "text")

                    // Timestamp continua a ser Long/Double normalmente
                    val timestamp = try {
                        child.child("timestamp").getValue(Long::class.java) ?: 0L
                    } catch (e: Exception) { 0L }

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

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
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
                .background(Color(0x80000000), RoundedCornerShape(16.dp))
                .border(2.dp, BorderColor, RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(message = msg)
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
                    .background(OtherBubbleColor, RoundedCornerShape(25.dp))
                    .border(2.dp, BorderColor, RoundedCornerShape(25.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = MyBubbleColor),
                modifier = Modifier
                    .size(50.dp)
                    .border(2.dp, BorderColor, RoundedCornerShape(50)),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Send, "Send", tint = Color.White)
            }
        }
    }
}

// --- CHAT BUBBLE ---
@Composable
fun ChatBubble(message: Message) {
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
        // --- CABEÇALHO ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = headerArrangement,
            modifier = Modifier
                .padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
                .fillMaxWidth()
        ) {
            // 1. Ícone do Jogador
            Image(
                painter = painterResource(id = getProfileDrawable(message.profileImageName)),
                contentDescription = "Avatar",
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // 2. Ícone da Picareta
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