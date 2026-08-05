package com.markrai.vaultist.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.markrai.vaultist.ui.ConfigurationState
import com.markrai.vaultist.ui.RootViewModel
import com.markrai.vaultist.ui.backlinks.BacklinksScreen
import com.markrai.vaultist.ui.browser.BrowserScreen
import com.markrai.vaultist.ui.components.VaultistSplashLogo
import com.markrai.vaultist.ui.image.ImageViewerScreen
import com.markrai.vaultist.ui.note.NoteScreen
import com.markrai.vaultist.ui.setup.SetupScreen

private const val GateRoute = "gate"
private const val SetupRoute = "setup"
private const val BrowserRoute = "browser"

@Composable
fun VaultistNavigation(
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
            VaultistSplashLogo()
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
                onOpenNote = { id, edit -> navController.navigate(noteRoute(id, edit = edit)) },
                onSettings = { navController.navigate(SetupRoute) },
            )
        }
        composable(
            route = "note/{id}?fragment={fragment}&edit={edit}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("fragment") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("edit") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            NoteScreen(
                onBack = navController::popBackStack,
                onOpenNote = { id, fragment -> navController.navigate(noteRoute(id, fragment)) },
                onOpenNoteForEdit = { id -> navController.navigate(noteRoute(id, edit = true)) },
                onBacklinks = { id -> navController.navigate(backlinksRoute(id)) },
                onOpenImage = { id -> navController.navigate(imageRoute(id)) },
                onDeleted = navController::popBackStack,
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

fun noteRoute(id: String, fragment: String? = null, edit: Boolean = false): String {
    val params = buildList {
        fragment?.let { add("fragment=${Uri.encode(it)}") }
        if (edit) add("edit=true")
    }
    return "note/${Uri.encode(id)}" + if (params.isEmpty()) "" else "?${params.joinToString("&")}"
}

fun backlinksRoute(id: String): String = "backlinks/${Uri.encode(id)}"
fun imageRoute(id: String): String = "image/${Uri.encode(id)}"
