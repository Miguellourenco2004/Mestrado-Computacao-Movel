package com.example.minequest

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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

    // Carrega os dados do utilizador (nome + imagem)
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
    LaunchedEffect(auth.currentUser) {
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

            InventoryGrid(slots = inventorySlots)

            Spacer(modifier = Modifier.height(32.dp))

            // Botão de logout
            Button(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screens.Login.route) {
                        popUpTo(0)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White
                )
            ) {
                Text("Logout", fontFamily = MineQuestFont)
            }
        }
    }
}

@Composable
fun InventorySlotView(slot: InventorySlot?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .border(2.dp, Color.Black)
            .background(Color(0xFF8D8D8D))
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
fun InventoryGrid(slots: List<InventorySlot>, rows: Int = 4, columns: Int = 6) {
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
                    InventorySlotView(
                        filledSlots[index],
                        modifier = Modifier.weight(1f)
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
        else -> R.drawable.bloco_pedra
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
