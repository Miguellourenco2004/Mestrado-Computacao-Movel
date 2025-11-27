package com.example.minequest

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.example.minequest.ui.theme.MineQuestFont

val MineGreen = Color(0xFF4CAF50)
val MineDarkGreen = Color(0xFF52A435)

data class Picareta(
    val nome: String,
    val imagemRes: Int,
    val custo: Int
)

@Composable
fun MineBlock(
    navController: NavController,
    viewModel: MineQuestViewModel = viewModel() // Injeta o ViewModel
) {

    //Lista de picaretas
    val listaPicaretas = listOf(
        Picareta("Wooden Pickaxe", R.drawable.madeira, 10),
        Picareta("Stone Pickaxe", R.drawable.pedra, 50),
        Picareta("Iron Pickaxe", R.drawable.ferro, 150),
        Picareta("Gold Pickaxe", R.drawable.ouro, 500),
        Picareta("Diamond Pickaxe", R.drawable.diamante, 1000),
        Picareta("Netherite Pickaxe", R.drawable.netherite, 1500)
    )

    // --- LER ESTADOS DO VIEWMODEL ---
    val indiceAtual by viewModel.pickaxeIndex.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(3000) // Espera 3 segundos
            viewModel.clearErrorMessage()
        }
    }

    val picaretaAtual = listaPicaretas.getOrNull(indiceAtual) ?: listaPicaretas.first()
    val proximaPicareta = listaPicaretas.getOrNull(indiceAtual + 1)
    val custoProximoUpgrade = proximaPicareta?.custo ?: 0

    val infiniteTransition = rememberInfiniteTransition(label = "PickaxeWobble")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = -15f, // Começa -15 graus (inclinada para a esquerda)
        targetValue = 15f,   // Vai até 15 graus (inclinada para a direita)
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing), // Duração de 0.8s
            repeatMode = RepeatMode.Reverse // Faz a animação reverter (ir e voltar)
        ),
        label = "PickaxeRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MineDarkGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Título "MineQuest"
            Text(
                text = "MineQuest",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
                fontFamily = MineQuestFont
            )

            // Área central com fundo verde
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f) // Mantém a largura do botão antigo
                        .height(70.dp)      // Mantém a altura
                        .padding(vertical = 8.dp) // Mantém o padding
                        .background( //
                            color = Color(0xFF323232),
                            shape = RoundedCornerShape(16.dp) // Cantos arredondados
                        )
                        .border(
                            width = 2.dp,
                            color = Color(0xFF6B3B25),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = picaretaAtual.nome,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MineQuestFont
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Image(
                    painter = painterResource(id = picaretaAtual.imagemRes),
                    contentDescription = picaretaAtual.nome,
                    modifier = Modifier
                        .size(150.dp)
                        .graphicsLayer {
                            rotationZ = rotationAngle
                        }
                )

                if (proximaPicareta != null) {
                    Spacer(modifier = Modifier.height(40.dp))

                    Button(
                        onClick = {
                            viewModel.upgradePickaxe(
                                custoDoUpgrade = custoProximoUpgrade,
                                maxIndex = listaPicaretas.size - 1
                            )
                        },
                        colors = ButtonDefaults.buttonColors(Color(0xFF323232)),
                        shape = RoundedCornerShape(40.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(70.dp)
                            .padding(vertical = 8.dp)
                            .border(
                                width = 2.dp,
                                color = Color(0xFF6B3B25),
                                shape = RoundedCornerShape(40.dp)
                            ),
                    ) {
                        Text(text = "Upgrade", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = MineQuestFont)
                    }
                    Text(
                        text = "Cost: $custoProximoUpgrade XP",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp),
                        fontFamily = MineQuestFont
                    )

                } else {
                    Spacer(modifier = Modifier.height(30.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {

                        Text(
                            text = "You have reached the max level!",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = MineQuestFont
                        )
                        Text(
                            text = "Congratulations!",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = MineQuestFont
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Image(
                            painter = painterResource(id = R.drawable.steve),
                            contentDescription = "Max Level",
                            modifier = Modifier
                                .size(175.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        fontFamily = MineQuestFont,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f)) // Empurra a Row para baixo

                Text(text = "All Pickaxes", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = MineQuestFont)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF323232),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 2.dp,
                            color = Color(0xFF6B3B25),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        listaPicaretas.forEachIndexed { index, picareta ->

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    painter = painterResource(id = picareta.imagemRes),
                                    contentDescription = picareta.nome,
                                    modifier = Modifier
                                        .size(40.dp)
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Spacer(
                                    modifier = Modifier
                                        .height(2.dp) // Altura do underline
                                        .width(40.dp)  // Largura
                                        .background(
                                            color = if (index == indiceAtual) Color.White else Color.Transparent
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}