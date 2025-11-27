package com.example.minequest

import android.content.Context
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.minequest.ui.theme.MineQuestFont
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import com.google.firebase.auth.FirebaseAuth

//  CORES DO TEMA
val ChatBackground = Color(0xFF4CAF50)
val MyBubbleColor = Color(0xFF388E3C)
val OtherBubbleColor = Color(0xFF323232)
val BorderColor = Color(0xFF6B3B25)

//  MODELO DE DADOS
data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val isMine: Boolean = false
)

//  VIEWMODEL
class ChatViewModel(private val context: Context) : ViewModel() {

    private val database = FirebaseDatabase.getInstance()
    private val chatRef = database.getReference("chat_global")

    private val usersRef = database.getReference("users")
    private val auth = FirebaseAuth.getInstance() // <-- Para saber quem está logado

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private var myUserId: String = ""
    private var myUserName: String = "Anonimo"

    init {
        // 1. Verificar quem é o utilizador logado
        val currentUser = auth.currentUser

        if (currentUser != null) {
            myUserId = currentUser.uid

            usersRef.child(myUserId).get().addOnSuccessListener { snapshot ->
                myUserName = snapshot.child("username").getValue(String::class.java) ?: "Mineiro"
            }.addOnFailureListener {
                myUserName = "Mineiro_Erro"
            }
        } else {
            myUserId = UUID.randomUUID().toString()
            myUserName = "Visitante"
        }

        listenToMessages()
    }

    private fun listenToMessages() {
        chatRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val listaMensagens = mutableListOf<Message>()

                for (child in snapshot.children) {
                    val id = child.key ?: ""
                    val senderId = child.child("senderId").getValue(String::class.java) ?: ""
                    val senderName = child.child("senderName").getValue(String::class.java) ?: "Unknown"
                    val text = child.child("text").getValue(String::class.java) ?: ""
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L

                    val msg = Message(
                        id = id,
                        senderId = senderId,
                        senderName = senderName,
                        text = text,
                        timestamp = timestamp,
                        // Compara com o ID do Auth ou o gerado
                        isMine = (senderId == myUserId)
                    )
                    listaMensagens.add(msg)
                }
                _messages.value = listaMensagens
            }

            override fun onCancelled(error: DatabaseError) {
                // ...
            }
        })
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val newMessageMap = hashMapOf(
            "senderId" to myUserId,
            "senderName" to myUserName, // <-- Agora envia o nome real!
            "text" to text,
            "timestamp" to System.currentTimeMillis()
        )

        chatRef.push().setValue(newMessageMap)
    }
}

//  FACTORY
class ChatViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

//  ECRÃ DO CHAT
@Composable
fun Chat(navController: NavHostController) {

    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(context))

    val messages by viewModel.messages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Scroll automático
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

        // Título
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

// --- BALÃO DE FALA ---
@Composable
fun ChatBubble(message: Message) {
    val bubbleColor = if (message.isMine) MyBubbleColor else OtherBubbleColor
    val alignment = if (message.isMine) Alignment.End else Alignment.Start

    // Alinha o texto do nome também à direita se for meu, ou à esquerda se for do outro
    val nameAlignment = if (message.isMine) Alignment.End else Alignment.Start

    val bubbleShape = if (message.isMine) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Text(
            text = message.senderName,
            color = Color.White.copy(alpha = 0.7f), // Um pouco transparente
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = MineQuestFont,
            modifier = Modifier.padding(
                start = 8.dp,
                end = 8.dp,
                bottom = 4.dp // Espaço entre o nome e a bolha
            )
        )

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