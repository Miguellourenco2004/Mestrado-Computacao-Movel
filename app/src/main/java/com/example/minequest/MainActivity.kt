package com.example.minequest

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minequest.ui.theme.MineQuestTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.minequest.navigation.NavGraph
import com.example.minequest.navigation.Screens
import androidx.navigation.compose.currentBackStackEntryAsState

// ✅ Firebase importações
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color.Companion.Green
import com.google.android.libraries.places.api.Places

class MainActivity : ComponentActivity() {


    private fun pedirPermissaoLocalizacao() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1001
            )
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pedirPermissaoLocalizacao()



        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, "AIzaSyDopLW7DqJf2wQG97_iiOuEKpYWj__arpo")
        }

        //  Escreve algo no Realtime Database
        val database = Firebase.database
        val ref = database.getReference("teste_minequest")

        ref.setValue("Olá Firebase!  App conectado com sucesso.")
            .addOnSuccessListener {
                println(" Dados enviados com sucesso!")
            }
            .addOnFailureListener { e ->
                println(" Erro ao enviar dados: ${e.message}")
            }

        setContent {
            MineQuestTheme {
                MineQuestApp()
            }
        }
    }
}



@Composable
fun MineQuestApp() {
    val navController = rememberNavController()

    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

    // Track user state reactively
    var currentUser by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(auth.currentUser) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    // If the user is not authenticated the start destination is the login page
    val startDestination = if (currentUser != null) {
        Screens.Map.route
    } else {
        Screens.Login.route
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF52A435),
        bottomBar = {
            if (currentUser != null) {
                MineQuestBottomBar(navController = navController)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            NavGraph(navController = navController, startDestination = startDestination, currentUser = currentUser)
        }
    }
}

@Composable
fun MineQuestBottomBar(navController: NavController) {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Color.White,
        unselectedIconColor = Color.LightGray,
        indicatorColor = Color(0xFF6B3B25)
    )

    BottomAppBar(containerColor = Color(0xFF513220)) {
        NavigationBarItem(
            icon = { Icon(Icons.Filled.LocationOn, contentDescription = "Map", tint = Color.White, modifier = Modifier.size(45.dp)) },
            selected = currentRoute == Screens.Map.route,
            onClick = { navController.navigate(Screens.Map.route) },
            colors = navItemColors
        )

        NavigationBarItem(
            icon = { Icon(Icons.Filled.Message, contentDescription = "Chat", tint = Color.White, modifier = Modifier.size(45.dp)) },
            selected = currentRoute == Screens.Chat.route,
            onClick = { navController.navigate(Screens.Chat.route) },
            colors = navItemColors
        )

        NavigationBarItem(
            icon = { Icon(painterResource(id = R.drawable.diamond_pickaxe), contentDescription = "Mineblock", tint = Color.White, modifier = Modifier.size(50.dp)) },
            selected = currentRoute == Screens.MineBlock.route,
            onClick = { navController.navigate(Screens.MineBlock.route) },
            colors = navItemColors
        )

        NavigationBarItem(
            icon = { Icon(Icons.Filled.Leaderboard, contentDescription = "Ranking", tint = Color.White, modifier = Modifier.size(45.dp)) },
            selected = currentRoute == Screens.Ranking.route,
            onClick = { navController.navigate(Screens.Ranking.route) },
            colors = navItemColors
        )

        NavigationBarItem(
            icon = { Icon(Icons.Filled.Person, contentDescription = "Profile", tint = Color.White, modifier = Modifier.size(45.dp)) },
            selected = currentRoute == Screens.Profile.route,
            onClick = { navController.navigate(Screens.Profile.route) },
            colors = navItemColors
        )
    }
}



@Preview(showBackground = true)
@Composable
fun MineQuestAppPreview() {
    MineQuestTheme {
        MineQuestApp()
    }
}
