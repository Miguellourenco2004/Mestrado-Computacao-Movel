package com.example.minequest

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.minequest.model.InventorySlot
import com.example.minequest.navigation.Screens
import com.example.minequest.ui.theme.MineQuestFont
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.example.minequest.data.QuestRepository
import com.example.minequest.model.DailyQuest
import com.example.minequest.model.QuestCatalog
import com.example.minequest.model.UserQuestProgress
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch


data class DailyQuestUiState(
    val isLoading: Boolean = true,
    val quests: Map<String, DailyQuest> = emptyMap(),
    val progress: Map<String, UserQuestProgress> = emptyMap(),
    val error: String? = null
)

@Composable
fun Profile(
    navController: NavController,
    currentUser: FirebaseUser?,
    modifier: Modifier = Modifier
) {
    val auth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance().getReference("users")

    val userId = auth.currentUser?.uid ?: "GUEST_USER_ID"

    val questRepository = remember(userId) { QuestRepository(userId) }
    var questUiState by remember { mutableStateOf(DailyQuestUiState()) }


    // Add quests to the database
    // uploadInitialQuestsToFirebase(auth)

    var username by remember { mutableStateOf("User") }
    var profileImageName by remember { mutableStateOf("") }
    var pontosXP by remember { mutableIntStateOf(0) }

    // Vai guardar qual o bloco a dropar
    var slotToDrop by remember { mutableStateOf<InventorySlot?>(null) }

    // Estado para recarregar a lista de blocos do inventário -> Qunado mudar os blocos do inventário
    // vão voltar a ser "fetched" na base de dados
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var questReloadTrigger by remember { mutableIntStateOf(0) }

    var inventorySlots by remember { mutableStateOf<List<InventorySlot>>(emptyList()) }

    // Ir buscar os dados do perfil do user
    LaunchedEffect(auth.currentUser) {
        auth.currentUser?.let { user ->
            loadUserData(
                user = user,
                database = database,
                onResult = { u, img, xp ->
                    username = u
                    profileImageName = img
                    pontosXP = xp
                }
            )
        }
    }

    val imageRes = getImageResourceByName(profileImageName)

    // Ir buscar o inventário do user
    LaunchedEffect(auth.currentUser, reloadTrigger) {
        auth.currentUser?.let { user ->
            loadInventory(
                user = user,
                database = database,
                onComplete = { slots ->
                    inventorySlots = slots
                }
            )
        }
    }

    // Missões globais e progresso individual
    LaunchedEffect(userId, questReloadTrigger) {
        questUiState = DailyQuestUiState(isLoading = true)
        questUiState = loadDailyQuestsState(questRepository)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = "Profile image",
                modifier = Modifier
                    .size(150.dp)
                    .padding(bottom = 5.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = username,
                fontFamily = MineQuestFont,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$pontosXP" + stringResource(id = R.string.xp),
                fontFamily = MineQuestFont,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFFF9800)
            )

            Spacer(modifier = Modifier.height(20.dp))

            InventoryGrid(
                slots = inventorySlots,
                onSlotClick = { slot ->
                    if (slot.blockId.startsWith("pickaxe_")) return@InventoryGrid
                    slotToDrop = slot
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            DailyQuestsDisplay(
                quests = questUiState.quests,
                progressMap = questUiState.progress,
                isLoading = questUiState.isLoading,
                errorMessage = questUiState.error
            )


            Spacer(modifier = Modifier.height(20.dp))


            Button(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screens.Login.route) {
                        popUpTo(0)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White
                ),
                shape = RectangleShape
            ) {
                Text(stringResource(id = R.string.logout), fontFamily = MineQuestFont)
            }
        }

        if (slotToDrop != null) {
            DropItemDialog(
                slot = slotToDrop!!,
                onDismiss = { slotToDrop = null },
                onConfirm = { quantityToDrop ->
                    auth.currentUser?.let { user ->
                        val itemRef = database.child(user.uid).child("inventory").child(slotToDrop!!.blockId)

                        // Ler a quantidade real no servidor antes de subtrair
                        itemRef.get().addOnSuccessListener { snapshot ->
                            val currentTotal = snapshot.getValue(Int::class.java) ?: 0
                            val newTotal = currentTotal - quantityToDrop

                            if (newTotal <= 0) {
                                itemRef.removeValue()
                            } else {
                                itemRef.setValue(newTotal)
                            }

                            // Forçar a atualização visual da grelha
                            reloadTrigger++
                            slotToDrop = null
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun InventorySlotView(slot: InventorySlot?, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Box(
        modifier = modifier
            .size(48.dp)
            .border(2.dp, Color.Black)
            .background(Color(0xFF8D8D8D))
            // Se o slot for diferente de null é clicável
            .clickable(enabled = slot != null) { onClick() }
    ) {
        if (slot != null) {
            Image(
                painter = painterResource(id = blockDrawable(slot.blockId)),
                contentDescription = slot.blockId,
                modifier = Modifier.fillMaxSize()
            )

            if (slot.quantity > 1) {
                Text(
                    text = slot.quantity.toString() + "x",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = MineQuestFont,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(2.dp)
                )
            }
        }
    }
}

@Composable
fun InventoryGrid(slots: List<InventorySlot>, rows: Int = 4, columns: Int = 6, onSlotClick: (InventorySlot) -> Unit = {}) {
    val totalSlots = rows * columns


    val trimmedSlots = slots.take(totalSlots)

    val filledSlots = trimmedSlots + List(totalSlots - trimmedSlots.size) { null }

    Column(
        modifier = Modifier
            .background(Color(0xFF3F3F3F))
            .padding(5.dp)
    ) {
        for (row in 0 until rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    val currentSlot = filledSlots.getOrNull(index)
                    InventorySlotView(
                        filledSlots[index],
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (currentSlot != null) {
                                onSlotClick(currentSlot)
                            }
                        }
                    )
                }
            }

            // Ads a space between each row
            if (row < rows - 1) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun DropItemDialog(
    slot: InventorySlot,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var quantityText by remember { mutableStateOf("1") }
    val maxQuantity = slot.quantity.toFloat()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        title = { Text(text = stringResource(id = R.string.drop_item), fontFamily = MineQuestFont) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = blockDrawable(slot.blockId)),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(stringResource(id = R.string.bloco) + ": ${slot.blockId}", fontFamily = MineQuestFont, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(20.dp))


                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {

                    IconButton(
                        onClick = {
                            val current = quantityText.toIntOrNull() ?: 0
                            if (current > 1) {
                                quantityText = (current - 1).toString()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Diminuir")
                    }


                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { newValue ->
                            val filteredValue = newValue.filter { it.isDigit() }

                            if (filteredValue.isEmpty()) {
                                quantityText = ""
                            } else {
                                val num = filteredValue.toIntOrNull() ?: 0

                                if (num <= maxQuantity) {
                                    quantityText = filteredValue
                                }
                            }
                        },
                        modifier = Modifier
                            .width(80.dp)
                            .padding(horizontal = 4.dp),
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            fontFamily = MineQuestFont,
                            fontSize = 20.sp
                        ),
                        shape = RectangleShape,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )


                    IconButton(

                        onClick = {
                            val current = quantityText.toIntOrNull() ?: 0
                            if (current < maxQuantity) {
                                quantityText = (current + 1).toString()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Aumentar")
                    }
                }


                Text(
                    text = "Max: ${maxQuantity.roundToInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 15.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalQty = quantityText.toIntOrNull() ?: 0
                    if (finalQty > 0) {
                        onConfirm(finalQty)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RectangleShape
            ) {
                Text(stringResource(id = R.string.drop), fontFamily = MineQuestFont,  fontSize = 15.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel), fontFamily = MineQuestFont, fontSize = 15.sp)
            }
        }
    )
}

@Composable
fun DailyQuestsDisplay(
    quests: Map<String, DailyQuest>,
    progressMap: Map<String, UserQuestProgress>,
    isLoading: Boolean,
    errorMessage: String?
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.missons),
            fontFamily = MineQuestFont,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Spacer(Modifier.height(10.dp))

        when {
            isLoading -> CircularProgressIndicator(Modifier.size(30.dp))
            errorMessage != null -> Text("Erro: $errorMessage", color = Color.Red, fontFamily = MineQuestFont)
            quests.isEmpty() -> Text(stringResource(R.string.no_active_missions), fontFamily = MineQuestFont)
            else -> quests.values.forEach { quest ->
                val progress = progressMap[quest.id] ?: UserQuestProgress()
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(vertical = 4.dp)
                        .background(Color(0xFF8D8D8D))
                        .border(1.dp, Color.Black)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = progress.isCompleted,
                        onCheckedChange = null,
                        enabled = false
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(quest.description, fontFamily = MineQuestFont, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = (progress.currentProgress / quest.target.toFloat()).coerceIn(0f,1f),
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                        Text("${progress.currentProgress} / ${quest.target}", fontSize = 12.sp)
                    }
                    Text("+${quest.reward} XP", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

fun loadUserData(
    user: FirebaseUser,
    database: DatabaseReference,
    onResult: (username: String, img: String, xp: Int) -> Unit
) {
    database.child(user.uid).get()
        .addOnSuccessListener { snap ->
            val username = snap.child("username").getValue(String::class.java) ?: "User"
            val img = snap.child("profileImage").getValue(String::class.java)
                ?: "minecraft_creeper_face"
            val xp = snap.child("pontosXP").getValue(Int::class.java) ?: 0

            onResult(username, img, xp)
        }
        .addOnFailureListener {
            onResult("User", "minecraft_creeper_face", 0)
        }
}


fun loadInventory(
    user: FirebaseUser,
    database: DatabaseReference,
    onComplete: (List<InventorySlot>) -> Unit
) {
    database.child(user.uid).child("pickaxeIndex").get()
        .addOnSuccessListener { pickaxeSnap ->

            val pickaxeIndex = pickaxeSnap.getValue(Int::class.java) ?: 1

            database.child(user.uid).child("inventory").get()
                .addOnSuccessListener { invSnap ->

                    val slots = mutableListOf<InventorySlot>()
                    for (item in invSnap.children) {
                        val id = item.key ?: continue
                        val qty = item.getValue(Int::class.java) ?: 0
                        slots += splitIntoSlots(id, qty)
                    }

                    val finalList = listOf(
                        InventorySlot("pickaxe_$pickaxeIndex", 1)
                    ) + slots

                    onComplete(finalList)
                }
        }
}




fun getImageResourceByName(name: String): Int {
    return when (name) {
        "fb9edad1e26f75" -> R.drawable._fb9edad1e26f75
        "4efed46e89c72955ddc7c77ad08b2ee" -> R.drawable._4efed46e89c72955ddc7c77ad08b2ee
        "578bfd439ef6ee41e103ae82b561986" -> R.drawable._578bfd439ef6ee41e103ae82b561986
        "faf3182a063a0f2a825cb39d959bae7" -> R.drawable._faf3182a063a0f2a825cb39d959bae7
        "a9a4ec03fa9afc407028ca40c20ed774" -> R.drawable.a9a4ec03fa9afc407028ca40c20ed774
        "big_villager_face" -> R.drawable.big_villager_face
        "images" -> R.drawable.images
        else -> R.drawable.minecraft_creeper_face
    }
}

// Gets the block image by it's name


private fun blockDrawable(id: String): Int {
    if (id.startsWith("pickaxe_")) {
        val index = id.removePrefix("pickaxe_").toIntOrNull() ?: 1
        return pickaxeDrawable(index)
    }

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

private fun pickaxeDrawable(index: Int): Int {
    return when(index) {
        0 -> R.drawable.madeira
        1 -> R.drawable.pedra
        2 -> R.drawable.ferro
        3-> R.drawable.ouro
        4 -> R.drawable.diamante
        else -> R.drawable.netherite
    }
}


// Divide the slots to a maximum of 64x block per stack
fun splitIntoSlots(blockId: String, quantity: Int): List<InventorySlot> {
    val slots = mutableListOf<InventorySlot>()
    var remaining = quantity
    while (remaining > 0) {
        val slotQty = minOf(64, remaining)
        slots.add(InventorySlot(blockId, slotQty))
        remaining -= slotQty
    }
    return slots
}

suspend fun loadDailyQuestsState(
    questRepository: QuestRepository
): DailyQuestUiState {

    val loadedQuests = questRepository.getOrCreateGlobalDailyQuests()

    val progressMap = loadedQuests.mapValues { (_, quest) ->
        questRepository.getOrCreateIndividualProgress(quest)
    }

    return DailyQuestUiState(
        isLoading = false,
        quests = loadedQuests,
        progress = progressMap
    )
}



// Helper function to add the Quests to the database
fun uploadInitialQuestsToFirebase(auth: FirebaseAuth) {
    val currentUser = auth.currentUser
    if (currentUser == null) {
        return
    }

    val database = Firebase.database
    val questRef = database.getReference("available_quests")

    QuestCatalog.allQuests.forEach { quest ->
        questRef.child(quest.id).setValue(quest)
            .addOnSuccessListener {
                println("Missão ${quest.id} adicionada/atualizada com sucesso.")
            }
            .addOnFailureListener { e ->
                println("Erro ao adicionar missão ${quest.id}: ${e.message}")
            }
    }
}
