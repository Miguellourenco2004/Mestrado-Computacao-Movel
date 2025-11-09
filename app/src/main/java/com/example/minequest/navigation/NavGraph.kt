package com.example.minequest.navigation
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.compose.runtime.Composable
import androidx.navigation.compose.composable
import com.example.minequest.Chat
import com.example.minequest.Map
import com.example.minequest.MineBlock
import com.example.minequest.Profile
import com.example.minequest.Ranking


@Composable
fun NavGraph (navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screens.Map.route
    ) {
        composable(route = Screens.Map.route){
            Map(navController = navController)
        }

        composable(route = Screens.Chat.route){
            Chat(navController = navController)
        }

        composable(route = Screens.Ranking.route){
            Ranking(navController = navController)
        }

        composable(route = Screens.Profile.route){
            Profile(navController = navController)
        }

        composable(route = Screens.MineBlock.route){
            MineBlock(navController = navController)
        }
    }
}
