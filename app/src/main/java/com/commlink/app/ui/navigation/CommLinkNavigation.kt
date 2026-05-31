package com.commlink.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.commlink.app.ui.messaging.MessagingScreen
import com.commlink.app.ui.ptt.PTTScreen

sealed class CommLinkRoute(val route: String) {
    object PTT : CommLinkRoute("ptt")
    object Messaging : CommLinkRoute("messaging")
}

@Composable
fun CommLinkNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = CommLinkRoute.PTT.route) {
        composable(CommLinkRoute.PTT.route) {
            PTTScreen()
        }
        composable(CommLinkRoute.Messaging.route) {
            MessagingScreen()
        }
    }
}
