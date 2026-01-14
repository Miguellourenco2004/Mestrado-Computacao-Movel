package com.example.minequest

import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.minequest.ui.theme.MineQuestFont
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- CORES ---
val ChatBackground = Color(0xFF52A435)
val MyBubbleColor = Color(0xFF52A435)
val OtherBubbleColor = Color(0xFF323232)
val TradeBubbleColor = Color(0xFFFFA000)
val BorderColor = Color(0xFF6B3B25)

// --- FUNÇÕES AUXILIARES DE IMAGEM ---
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

data class ChatInventorySlot(val blockId: String, val quantity: Int)

fun getBlockDrawableChat(id: String): Int {
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

// --- ECRÃ PRINCIPAL DO CHAT ---
@Composable
fun Chat(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(context))
    val messages by viewModel.messages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // --- ESTADO PARA AS ABAS (Global vs Inbox) ---
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Global, 1 = Inbox
    val myUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var selectedUserMessage by remember { mutableStateOf<Message?>(null) }
    var showTradeDialog by remember { mutableStateOf(false) }
    var tradeErrorMessage by remember { mutableStateOf<String?>(null) }

    // Scroll automático
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Dialog de Perfil
    if (selectedUserMessage != null) {
        UserProfileDialog(
            message = selectedUserMessage!!,
            onDismiss = { selectedUserMessage = null }
        )
    }

    // Dialog de Proposta (Modo Global, targetUserId = null)
    if (showTradeDialog) {
        TradeProposalDialog(
            viewModel = viewModel,
            onDismiss = { showTradeDialog = false },
            targetUserId = null
        )
    }

    // Dialog de Erro
    if (tradeErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { tradeErrorMessage = null },
            title = { Text("Info", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = MineQuestFont, textAlign = TextAlign.Center) },
            text = { Text(tradeErrorMessage!!, fontFamily = MineQuestFont, textAlign = TextAlign.Center) },
            confirmButton = {
                Button(onClick = { tradeErrorMessage = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), shape = RectangleShape) {
                    Text("OK", color = Color.White, fontFamily = MineQuestFont)
                }
            },
            containerColor = Color(0xFFC6C6C6),
            shape = RectangleShape,
            modifier = Modifier.border(2.dp, Color.Black)
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(ChatBackground).padding(16.dp).imePadding()
    ) {
        // --- CABEÇALHO ---
        Text(
            text = if (selectedTab == 0) "Global Chat" else "Trades",
            fontFamily = MineQuestFont,
            fontSize = 28.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
        )

        // --- BOTÕES DAS ABAS ---
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { selectedTab = 0 },
                colors = ButtonDefaults.buttonColors(containerColor = if (selectedTab == 0) MyBubbleColor else Color.Gray),
                shape = RectangleShape,
                modifier = Modifier.weight(1f).border(2.dp, BorderColor, RectangleShape)
            ) { Text("Global", fontFamily = MineQuestFont, color = Color.White) }

            Button(
                onClick = { selectedTab = 1 },
                colors = ButtonDefaults.buttonColors(containerColor = if (selectedTab == 1) TradeBubbleColor else Color.Gray),
                shape = RectangleShape,
                modifier = Modifier.weight(1f).border(2.dp, BorderColor, RectangleShape)
            ) { Text("Inbox", fontFamily = MineQuestFont, color = Color.White) }
        }

        // --- LISTA DE MENSAGENS ---
        Box(
            modifier = Modifier.weight(1f).background(Color(0x80000000), RectangleShape).border(2.dp, BorderColor, RectangleShape).padding(8.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Filtro (Global vs Inbox)
                val filteredMessages = messages.filter { msg ->
                    if (selectedTab == 0) {
                        !msg.isTrade || msg.targetId.isEmpty()
                    } else {
                        msg.isTrade && (msg.targetId == myUserId || (msg.senderId == myUserId && msg.targetId.isNotEmpty()))
                    }
                }

                items(filteredMessages) { msg ->
                    ChatBubble(
                        message = msg,
                        onHeaderClick = { selectedUserMessage = msg },
                        onAcceptTrade = { messageToAccept ->
                            viewModel.acceptTrade(
                                messageToAccept,
                                onSuccess = {
                                    Toast.makeText(context, "Troca iniciada! A calcular rota...", Toast.LENGTH_SHORT).show()
                                },
                                onError = { errorMsg -> tradeErrorMessage = errorMsg }
                            )
                        },
                        onCancelTrade = { messageToCancel ->
                            viewModel.cancelTrade(messageToCancel)
                        },
                        // --- AQUI ESTÁ A LIGAÇÃO QUE FALTAVA ---
                        onFinalizeTrade = { messageToFinalize ->
                            viewModel.finalizeTrade(messageToFinalize)
                            Toast.makeText(context, "Troca Completa! XP Ganho!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- INPUT (Apenas na aba Global) ---
        if (selectedTab == 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { showTradeDialog = true },
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = TradeBubbleColor),
                    modifier = Modifier.size(50.dp).border(2.dp, BorderColor, RectangleShape),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = stringResource(id = R.string.trade), color = Color.White, fontFamily = MineQuestFont, fontSize = 10.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = TextStyle(color = Color.White, fontFamily = MineQuestFont, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.weight(1f).height(50.dp).background(OtherBubbleColor, RectangleShape).border(2.dp, BorderColor, RectangleShape).padding(horizontal = 20.dp, vertical = 14.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    },
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MyBubbleColor),
                    modifier = Modifier.size(50.dp).border(2.dp, BorderColor, RectangleShape),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Send, "Send", tint = Color.White)
                }
            }
        }
    }
}

// --- DIALOG DE PROPOSTA DE TROCA ---
@Composable
fun TradeProposalDialog(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    targetUserId: String? = null
) {
    val targetAvailableBlocks by viewModel.targetInventory.collectAsState()
    val myAvailableBlocks by viewModel.myInventory.collectAsState()

    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        viewModel.loadMyInventory()
        viewModel.loadTargetInventory(targetUserId)
        delay(500)
        isLoading = false
    }

    var offerBlock by remember { mutableStateOf("") }
    var offerAmount by remember { mutableStateOf("1") }

    var requestBlock by remember { mutableStateOf("") }
    var requestAmount by remember { mutableStateOf("1") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Atualiza seleção default
    LaunchedEffect(myAvailableBlocks) {
        if (myAvailableBlocks.isNotEmpty()) offerBlock = myAvailableBlocks[0]
    }
    LaunchedEffect(targetAvailableBlocks) {
        if (targetAvailableBlocks.isNotEmpty()) requestBlock = targetAvailableBlocks[0]
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFC6C6C6), RectangleShape).border(3.dp, Color.Black, RectangleShape).padding(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (targetUserId == null) "GLOBAL TRADE" else "PRIVATE TRADE",
                    fontFamily = MineQuestFont, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))

                // YOU GIVE
                Text("You Give (Your Items):", fontFamily = MineQuestFont, fontSize = 14.sp, color = Color.Black)
                if (isLoading) {
                    Text("Checking...", fontSize = 12.sp, fontFamily = MineQuestFont, color = Color.DarkGray)
                } else if (myAvailableBlocks.isEmpty()) {
                    Text("You have no items!", fontSize = 12.sp, fontFamily = MineQuestFont, color = Color.Red)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                if (offerBlock.isNotEmpty()) {
                                    val nextIndex = (myAvailableBlocks.indexOf(offerBlock) + 1) % myAvailableBlocks.size
                                    offerBlock = myAvailableBlocks[nextIndex]
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            shape = RectangleShape, modifier = Modifier.border(1.dp, Color.Black)
                        ) {
                            if (offerBlock.isNotEmpty()) {
                                Image(painter = painterResource(getBlockDrawableChat(offerBlock)), contentDescription = null, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(value = offerAmount, onValueChange = { if(it.all { char -> char.isDigit() }) offerAmount = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(fontFamily = MineQuestFont, fontSize = 18.sp, textAlign = TextAlign.Center), modifier = Modifier.width(50.dp).background(Color.White).border(1.dp, Color.Black).padding(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Icon(Icons.Default.SwapHoriz, "Trade", tint = Color.White, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(16.dp))

                // YOU WANT
                Text("You Want:", fontFamily = MineQuestFont, fontSize = 14.sp, color = Color.Black)
                if (isLoading) {
                    Text("Checking...", fontSize = 12.sp, fontFamily = MineQuestFont, color = Color.DarkGray)
                } else if (targetAvailableBlocks.isEmpty()) {
                    Text("No items available!", fontSize = 12.sp, fontFamily = MineQuestFont, color = Color.Red)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                if (requestBlock.isNotEmpty()) {
                                    val nextIndex = (targetAvailableBlocks.indexOf(requestBlock) + 1) % targetAvailableBlocks.size
                                    requestBlock = targetAvailableBlocks[nextIndex]
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            shape = RectangleShape, modifier = Modifier.border(1.dp, Color.Black)
                        ) {
                            if (requestBlock.isNotEmpty()) {
                                Image(painter = painterResource(getBlockDrawableChat(requestBlock)), contentDescription = null, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(value = requestAmount, onValueChange = { if(it.all { char -> char.isDigit() }) requestAmount = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(fontFamily = MineQuestFont, fontSize = 18.sp, textAlign = TextAlign.Center), modifier = Modifier.width(50.dp).background(Color.White).border(1.dp, Color.Black).padding(8.dp))
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMessage!!, color = Color.Red, fontSize = 12.sp, fontFamily = MineQuestFont)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), shape = RectangleShape) { Text("Cancel", fontFamily = MineQuestFont) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            val oAmt = offerAmount.toIntOrNull() ?: 0
                            val rAmt = requestAmount.toIntOrNull() ?: 0
                            if (myAvailableBlocks.isEmpty()) {
                                errorMessage = "You have nothing!"
                            } else if (requestBlock.isEmpty()) {
                                errorMessage = "Invalid Request"
                            } else {
                                viewModel.sendTradeProposal(offerBlock, oAmt, requestBlock, rAmt, targetUserId, onSuccess = { onDismiss() }, onError = { msg -> errorMessage = msg })
                            }
                        },
                        enabled = !isLoading && myAvailableBlocks.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RectangleShape
                    ) { Text("Propose", fontFamily = MineQuestFont) }
                }
            }
        }
    }
}

//  DIALOG DE PERFIL DO UTILIZADOR
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
        } else { isLoading = false }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).background(Color(0xFF388E3C), RectangleShape).border(3.dp, BorderColor, RectangleShape).padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("Player Profile", fontFamily = MineQuestFont, fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
                Image(painter = painterResource(id = getProfileDrawable(message.profileImageName)), contentDescription = null, modifier = Modifier.size(100.dp).border(2.dp, BorderColor).background(Color(0xFF323232)).padding(8.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(message.senderName, fontFamily = MineQuestFont, fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(id = getPickaxeImage(message.pickaxeIndex)), contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Level ${message.pickaxeIndex + 1}", fontFamily = MineQuestFont, fontSize = 16.sp, color = Color.Yellow)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Inventory", fontFamily = MineQuestFont, fontSize = 18.sp, color = Color.White, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) CircularProgressIndicator(color = Color.White)
                else if (inventorySlots.isEmpty()) Text("Empty Inventory", color = Color.Gray, fontFamily = MineQuestFont)
                else ChatInventoryGrid(slots = inventorySlots, columns = 4)

                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = BorderColor), shape = RectangleShape, modifier = Modifier.fillMaxWidth()) {
                    Text("Close", fontFamily = MineQuestFont, color = Color.White)
                }
            }
        }
    }
}

// GRID DE INVENTÁRIO (AUXILIAR)
@Composable
fun ChatInventoryGrid(slots: List<ChatInventorySlot>, columns: Int = 4) {
    val rowsNeeded = (slots.size + columns - 1) / columns
    val rows = maxOf(rowsNeeded, 3)
    val totalSlots = rows * columns
    val filledSlots = slots + List((totalSlots - slots.size).coerceAtLeast(0)) { null }
    Column(modifier = Modifier.background(Color(0xFFC6C6C6)).border(2.dp, Color.Black).padding(4.dp)) {
        for (row in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index < filledSlots.size) ChatInventorySlotView(filledSlots[index], modifier = Modifier.weight(1f))
                    else ChatInventorySlotView(null, modifier = Modifier.weight(1f))
                }
            }
            if (row < rows - 1) Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
fun ChatInventorySlotView(slot: ChatInventorySlot?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.aspectRatio(1f).background(Color(0xFF8B8B8B)).border(1.dp, Color(0xFF373737)).padding(1.dp)) {
        if (slot != null) {
            Image(painter = painterResource(id = getBlockDrawableChat(slot.blockId)), contentDescription = slot.blockId, modifier = Modifier.fillMaxSize())
            if (slot.quantity > 1) Text(text = "${slot.quantity}", color = Color.White, fontSize = 10.sp, fontFamily = MineQuestFont, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd))
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
fun ChatBubble(
    message: Message,
    onHeaderClick: () -> Unit,
    onAcceptTrade: (Message) -> Unit,
    onCancelTrade: (Message) -> Unit,
    onFinalizeTrade: (Message) -> Unit // NOVO PARÂMETRO
) {
    val bubbleColor = if (message.isTrade) TradeBubbleColor else if (message.isMine) MyBubbleColor else OtherBubbleColor
    val alignment = if (message.isMine) Alignment.End else Alignment.Start
    val headerArrangement = if (message.isMine) Arrangement.End else Arrangement.Start

    val isGrayedOut = message.isCompleted || message.isCancelled
    val bubbleBg = if (isGrayedOut && message.isTrade) Color.Gray else bubbleColor
    val tradeTextColor = if (isGrayedOut) Color.DarkGray else Color.White

    val bubbleShape = if (message.isMine) RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp) else RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)

    //  LÓGICA DE TEMPORIZADOR
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    if (message.isTrade && message.arrivalTimestamp > 0 && !message.isCompleted) {
        LaunchedEffect(Unit) {
            while (true) {
                currentTime = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = headerArrangement,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp).fillMaxWidth().clickable { onHeaderClick() }
        ) {
            Image(painter = painterResource(id = getProfileDrawable(message.profileImageName)), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Image(painter = painterResource(id = getPickaxeImage(message.pickaxeIndex)), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = message.senderName, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = MineQuestFont)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = formatTime(message.timestamp), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = MineQuestFont)
        }

        Surface(color = bubbleBg, shape = bubbleShape, border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor), modifier = Modifier.widthIn(max = 280.dp)) {
            if (message.isTrade) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {

                    val statusText = when {
                        message.isCancelled -> "TRADE CANCELLED"
                        message.isCompleted -> "TRADE COMPLETED"
                        else -> "TRADE OFFER"
                    }
                    val statusColor = if(message.isCancelled) Color(0xFF8B0000) else tradeTextColor

                    Text(text = statusText, fontFamily = MineQuestFont, fontWeight = FontWeight.Bold, color = statusColor)

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "You receive:", fontFamily = MineQuestFont, color = Color.White, fontSize = 14.sp)
                            Image(painter = painterResource(getBlockDrawableChat(message.offerBlock)), contentDescription = null, modifier = Modifier.size(32.dp))
                            Text("${message.offerAmount}x", fontFamily = MineQuestFont, color = Color.White)
                        }
                        Icon(Icons.Default.SwapHoriz, "Trade", tint = Color.White)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "You give:", fontFamily = MineQuestFont, color = Color.White, fontSize = 14.sp)
                            Image(painter = painterResource(getBlockDrawableChat(message.requestBlock)), contentDescription = null, modifier = Modifier.size(32.dp))
                            Text("${message.requestAmount}x", fontFamily = MineQuestFont, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!message.isCompleted && !message.isCancelled) {


                        if (message.arrivalTimestamp == 0L) {
                            if (message.isMine) {
                                Button(
                                    onClick = { onCancelTrade(message) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                                    shape = RectangleShape,
                                    modifier = Modifier.height(35.dp)
                                ) {
                                    Text("CANCEL", color = Color.White, fontFamily = MineQuestFont, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { onAcceptTrade(message) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    shape = RectangleShape,
                                    modifier = Modifier.height(35.dp)
                                ) {
                                    val mins = message.deliveryTimeMillis / 60000
                                    Text("ACCEPT (${mins}m)", color = Color.Black, fontFamily = MineQuestFont, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        else {
                            val timeLeft = message.arrivalTimestamp - currentTime

                            if (timeLeft > 0) {
                                // A contar...
                                Text(
                                    "Arriving in: ${timeLeft / 1000}s",
                                    color = Color.Yellow,
                                    fontFamily = MineQuestFont,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                // Tempo acabou: REIVINDICAR
                                Button(
                                    onClick = { onFinalizeTrade(message) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), // Verde
                                    shape = RectangleShape,
                                    modifier = Modifier.height(35.dp)
                                ) {
                                    Text("CLAIM (+${message.xpReward} XP)", color = Color.White, fontFamily = MineQuestFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                Text(text = message.text, color = Color.White, fontFamily = MineQuestFont, fontSize = 16.sp, modifier = Modifier.padding(12.dp))
            }
        }
    }
}