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


@Composable
fun Profile(
    navController: NavController,
    currentUser: FirebaseUser?,
    modifier: Modifier = Modifier
) {
    val auth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance().getReference("users")

    var username by remember { mutableStateOf("User") }
    var profileImageName by remember { mutableStateOf("") }
    var pontosXP by remember { mutableStateOf(0) }

    // Vai guardar qual o bloco a dropar
    var slotToDrop by remember { mutableStateOf<InventorySlot?>(null) }

    // Estado para recarregar a lista de blocos do inventário -> Qunado mudar os blocos do inventário
    // vão voltar a ser "fetched" na base de dados
    var reloadTrigger by remember { mutableIntStateOf(0) }

    // Carrega os dados do utilizador (nome + imagem + pontosXP)
    LaunchedEffect(auth.currentUser) {
        auth.currentUser?.let { user ->
            database.child(user.uid).get()
                .addOnSuccessListener { snapshot ->
                    username = snapshot.child("username").getValue(String::class.java) ?: "User"
                    profileImageName = snapshot.child("profileImage").getValue(String::class.java)
                        ?: "minecraft_creeper_face"
                    pontosXP = snapshot.child("pontosXP").getValue(Int::class.java) ?: 0
                }
                .addOnFailureListener {
                    username = "User"
                    pontosXP = 0
                }
        }
    }

    var inventorySlots by remember { mutableStateOf<List<InventorySlot>>(emptyList()) }

    // Carrega o inventário do utilizador
    LaunchedEffect(auth.currentUser, reloadTrigger) {
        auth.currentUser?.let { user ->
            database.child(user.uid).child("inventory").get()
                .addOnSuccessListener { snapshot ->
                    val slots = mutableListOf<InventorySlot>()
                    for (item in snapshot.children) {
                        val blockId = item.key ?: continue
                        val quantity = item.getValue(Int::class.java) ?: 0
                        slots += splitIntoSlots(blockId, quantity)
                    }
                    inventorySlots = slots
                }
        }
    }


    val imageRes = getImageResourceByName(profileImageName)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Imagem do utilizador
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = "Profile image",
                modifier = Modifier
                    .size(150.dp)
                    .padding(bottom = 5.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Nome do utilizador
            Text(
                text = "$username",
                fontFamily = MineQuestFont,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$pontosXP XP Points",
                fontFamily = MineQuestFont,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFFF9800)
            )

            Spacer(modifier = Modifier.height(20.dp))

            InventoryGrid(
                slots = inventorySlots,
                onSlotClick = { slot ->
                    slotToDrop = slot
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Botão de logout
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
                Text("Logout", fontFamily = MineQuestFont)
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
    val filledSlots = slots + List(totalSlots - slots.size) { null }

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
        title = { Text(text = "Drop item?", fontFamily = MineQuestFont) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = blockDrawable(slot.blockId)),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("BLock: ${slot.blockId}", fontFamily = MineQuestFont, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(20.dp))

                // --- ZONA DE INPUT (MENOS | TEXTO | MAIS) ---
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

                    // Botão MAIS (+)
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

                // Texto de ajuda (Ex: "Máx: 64")
                Text(
                    text = "Máx: ${maxQuantity.roundToInt()}",
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
                Text("Drop", fontFamily = MineQuestFont,  fontSize = 15.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = MineQuestFont, fontSize = 15.sp)
            }
        }
    )
}


// Gets the profile image drawbale
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
fun blockDrawable(id: String): Int {
    return when (id) {
        "diamond" -> R.drawable.bloco_diamante
        "emerald" -> R.drawable.bloco_esmeralda
        "gold" -> R.drawable.bloco_ouro
        "coal" -> R.drawable.bloco_carvao
        else -> R.drawable.bloco_terra
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

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ProfilePreview() {
    Profile(
        navController = NavController(LocalContext.current),
        currentUser = null
    )
}
