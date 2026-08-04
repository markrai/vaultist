package com.vaultview.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vaultview.ui.ConfigurationState
import com.vaultview.ui.RootViewModel
import com.vaultview.ui.backlinks.BacklinksScreen
import com.vaultview.ui.browser.BrowserScreen
import com.vaultview.ui.image.ImageViewerScreen
import com.vaultview.ui.note.NoteScreen
import com.vaultview.ui.setup.SetupScreen

private const val GateRoute = "gate"
private const val SetupRoute = "setup"
private const val BrowserRoute = "browser"

@Composable
fun VaultViewNavigation(
    navController: NavHostController = rememberNavController(),
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    NavHost(navController = navController, startDestination = GateRoute) {
        composable(GateRoute) {
            val configuration by rootViewModel.configuration.collectAsStateWithLifecycle()
            LaunchedEffect(configuration) {
                when (configuration) {
                    ConfigurationState.Configured -> navController.navigate(BrowserRoute) { popUpTo(GateRoute) { inclusive = true } }
                    ConfigurationState.Missing -> navController.navigate(SetupRoute) { popUpTo(GateRoute) { inclusive = true } }
                    ConfigurationState.Loading -> Unit
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        composable(SetupRoute) {
            SetupScreen(onSaved = {
                if (!navController.popBackStack(BrowserRoute, inclusive = false)) {
                    navController.navigate(BrowserRoute) { popUpTo(SetupRoute) { inclusive = true } }
                }
            }, onBack = navController::popBackStack)
        }
        composable(BrowserRoute) {
            BrowserScreen(
                onOpenFolder = {},
                onOpenNote = { id -> navController.navigate(noteRoute(id)) },
                onSettings = { navController.navigate(SetupRoute) },
            )
        }
        composable(
            route = "note/{id}?fragment={fragment}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("fragment") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            NoteScreen(
                onBack = navController::popBackStack,
                onOpenNote = { id, fragment -> navController.navigate(noteRoute(id, fragment)) },
                onBacklinks = { id -> navController.navigate(backlinksRoute(id)) },
                onOpenImage = { id -> navController.navigate(imageRoute(id)) },
            )
        }
        composable("backlinks/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            BacklinksScreen(
                onBack = { navController.popBackStack() },
                onOpenSource = { id, line -> navController.navigate(noteRoute(id, "line-$line")) },
            )
        }
        composable("image/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            ImageViewerScreen(onBack = navController::popBackStack)
        }
    }
}

fun noteRoute(id: String, fragment: String? = null): String =
    "note/${Uri.encode(id)}" + (fragment?.let { "?fragment=${Uri.encode(it)}" } ?: "")

fun backlinksRoute(id: String): String = "backlinks/${Uri.encode(id)}"
fun imageRoute(id: String): String = "image/${Uri.encode(id)}"
