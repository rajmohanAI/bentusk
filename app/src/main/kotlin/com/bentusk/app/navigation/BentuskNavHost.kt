package com.bentusk.app.navigation

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bentusk.app.feature.covers.CoversScreen
import com.bentusk.app.feature.editor.EditorScreen
import com.bentusk.app.feature.export.ExportScreen
import com.bentusk.app.feature.home.HomeScreen
import com.bentusk.app.feature.planner.PlannerScreen
import com.bentusk.app.feature.pro.ProScreen

@Composable
fun BentuskNavHost(sharedPhoto: Uri?, onSharedPhotoHandled: () -> Unit) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    val upgrade: () -> Unit = { nav.navigate(ProRoute) { launchSingleTop = true } }

    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                sharedPhoto = sharedPhoto,
                onSharedPhotoHandled = onSharedPhotoHandled,
                onOpenEditor = { mode, uri -> nav.navigate(EditorRoute(mode.name, uri.toString())) },
                onOpenPlanner = { nav.navigate(PlannerRoute) },
                onOpenCovers = { nav.navigate(CoversRoute) },
                onOpenPro = upgrade,
            )
        }
        composable<EditorRoute> {
            EditorScreen(onBack = back, onExport = { nav.navigate(ExportRoute) }, onUpgrade = upgrade)
        }
        composable<ExportRoute> {
            // Done returns home; the editor stays one step back if the user presses Back instead.
            ExportScreen(onDone = { nav.popBackStack(HomeRoute, inclusive = false) })
        }
        composable<PlannerRoute> {
            PlannerScreen(onBack = back, onUpgrade = upgrade)
        }
        composable<CoversRoute> {
            CoversScreen(onBack = back, onExport = { nav.navigate(ExportRoute) }, onUpgrade = upgrade)
        }
        composable<ProRoute>(
            enterTransition = { slideInVertically { it } },
            exitTransition = { slideOutVertically { it } },
        ) {
            ProScreen(onClose = back)
        }
    }
}
