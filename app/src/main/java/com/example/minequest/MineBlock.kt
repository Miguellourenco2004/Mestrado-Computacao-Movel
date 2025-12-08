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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.example.minequest.ui.theme.MineQuestFont
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource


val MineDarkGreen = Color(0xFF52A435)

data class Picareta(
    val nome: String,
    val imagemRes: Int,
    val custo_int: Int,
    val culto_blocos: Map<String, Int>,
)

@Composable
fun MineBlock(
    navController: NavController,
    viewModel: MineQuestViewModel = viewModel() // Injeta o ViewModel
) {

    val wood_pickaxe_blocks_cost = mapOf(
        "grass" to 1
    )

    val stone_pickaxe_blocks_cost = mapOf(
        "stone" to 10,
        "wood" to 2
    )

    val iron_pickaxe_blocks_cost = mapOf(
        "iron" to 20,
        "wood" to 10,
        "stone" to 20,
    )

    val gold_pickaxe_blocks_cost = mapOf(
        "iron" to 5,
        "wood" to 20,
        "stone" to 20,
        "gold" to 30,
    )

    val diamomd_pickaxe_blocks_cost = mapOf(
        "wood" to 100,
        "stone" to 50,
        "diamond" to 40,
    )
    val netherite_pickaxe_blocks_cost = mapOf(
        "wood" to 100,
        "neder" to 50,
        "diamond" to 10,
        "emerald" to 10,
        "iron" to 10,
    )



    //Lista de picaretas
    val listaPicaretas = listOf(
        Picareta("Wooden Pickaxe", R.drawable.madeira, 0, wood_pickaxe_blocks_cost),
        Picareta("Stone Pickaxe", R.drawable.pedra, 50, stone_pickaxe_blocks_cost),
        Picareta("Iron Pickaxe", R.drawable.ferro, 150, iron_pickaxe_blocks_cost),
        Picareta("Gold Pickaxe", R.drawable.ouro, 500, gold_pickaxe_blocks_cost),
        Picareta("Diamond Pickaxe", R.drawable.diamante, 1000, diamomd_pickaxe_blocks_cost),
        Picareta("Netherite Pickaxe", R.drawable.netherite, 1500, netherite_pickaxe_blocks_cost)
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
    val custoProximoUpgrade = proximaPicareta?.custo_int ?: 0
    val custoBlocoProximoUpgrade = proximaPicareta?.culto_blocos ?: emptyMap()

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
                text = stringResource(id = R.string.minequest),
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
                            shape = RectangleShape// Cantos arredondados
                        )
                        .border(
                            width = 2.dp,
                            color = Color(0xFF6B3B25),
                            shape = RectangleShape
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
                                custoDoUpgradebloco = custoBlocoProximoUpgrade,
                                maxIndex = listaPicaretas.size - 1
                            )
                        },
                        colors = ButtonDefaults.buttonColors(Color(0xFF323232)),
                        shape = RectangleShape,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(70.dp)
                            .padding(vertical = 8.dp)
                            .border(
                                width = 2.dp,
                                color = Color(0xFF6B3B25),
                                shape = RectangleShape
                            ),
                    ) {
                        Text(text = stringResource(id = R.string.upgrade), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = MineQuestFont)
                    }
                    Column (
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ){
                        Text(
                            text = stringResource(R.string.cost, custoProximoUpgrade),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp),
                            fontFamily = MineQuestFont,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Column {
                            custoBlocoProximoUpgrade
                                .toList()
                                .chunked(4)
                                .forEach { linha ->

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        linha.forEach { (blockId, quantidade) ->

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Image(
                                                    painter = painterResource(id = blockDrawable(blockId)),
                                                    contentDescription = blockId,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "x$quantidade",
                                                    color = Color.White,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = MineQuestFont
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                    }
                                }
                        }

                    }

                } else {
                    Spacer(modifier = Modifier.height(30.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {

                        Text(
                            text = stringResource(id = R.string.max_level),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = MineQuestFont,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(id = R.string.congratulations),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = MineQuestFont
                        )
                        Spacer(modifier = Modifier.height(15.dp))

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
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f)) // Empurra a Row para baixo

                Text(text = stringResource(id = R.string.all_pickaxes), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = MineQuestFont)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF323232),
                            shape = RectangleShape
                        )
                        .border(
                            width = 2.dp,
                            color = Color(0xFF6B3B25),
                            shape = RectangleShape
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


private fun blockDrawable(id: String): Int {
    return when (id) {
        "diamond" -> R.drawable.bloco_diamante
        "emerald" -> R.drawable.bloco_esmeralda
        "gold" -> R.drawable.bloco_ouro
        "coal" -> R.drawable.bloco_carvao
        "iron" -> R.drawable.bloco_iron
        "stone" -> R.drawable.bloco_pedra
        "dirt" -> R.drawable.bloco_terra
        "grass" -> R.drawable.grace
        "wood" -> R.drawable.wood
        "lapis" -> R.drawable.lapis
        "neder" -> R.drawable.netherite_b
        else -> R.drawable.bloco_terra
    }
}
