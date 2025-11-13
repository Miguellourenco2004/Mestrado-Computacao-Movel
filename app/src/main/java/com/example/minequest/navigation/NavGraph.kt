package com.example.minequest.navigation
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.compose.runtime.Composable
import androidx.navigation.compose.composable
import com.example.minequest.Chat
import com.example.minequest.LoginScreen
import com.example.minequest.Map
import com.example.minequest.MineBlock
import com.example.minequest.Profile
import com.example.minequest.Ranking
import com.example.minequest.RegisterScreen
import com.google.firebase.auth.FirebaseUser


@Composable
fun NavGraph(navController: NavHostController, startDestination: String,  currentUser: FirebaseUser? = null) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(route = Screens.Map.route) {
            Map(navController = navController)
        }

        composable(route = Screens.Chat.route) {
            Chat(navController = navController)
        }

        composable(route = Screens.Ranking.route) {
            Ranking(navController = navController, currentUser = currentUser)
        }

        composable(route = Screens.Profile.route) {
            Profile(navController = navController, currentUser = currentUser)
        }

        composable(route = Screens.MineBlock.route) {
            MineBlock(navController = navController)
        }

        composable(route = Screens.Login.route) {
            LoginScreen(navController = navController)
        }

        composable(route = Screens.Register.route) {
            RegisterScreen(navController = navController)
        }
    }
}

