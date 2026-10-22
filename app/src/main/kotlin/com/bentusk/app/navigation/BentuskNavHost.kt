package com.bentusk.app.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import com.bentusk.app.feature.studio.StudioScreen

private const val DURATION = 320

@Composable
fun BentuskNavHost(sharedPhoto: Uri?, onSharedPhotoHandled: () -> Unit) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    val upgrade: () -> Unit = { nav.navigate(ProRoute) { launchSingleTop = true } }
    val ease = tween<Float>(DURATION, easing = FastOutSlowInEasing)

    // Screens slide in from the side with a light fade, and the screen behind eases back slightly,
    // so moving through the app feels continuous rather than cut.
    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(DURATION, easing = FastOutSlowInEasing)) +
                fadeIn(ease)
        },
        exitTransition = { scaleOut(ease, targetScale = 0.96f) + fadeOut(tween(DURATION / 2)) },
        popEnterTransition = { scaleIn(ease, initialScale = 0.96f) + fadeIn(ease) },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(DURATION, easing = FastOutSlowInEasing)) +
                fadeOut(ease)
        },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                sharedPhoto = sharedPhoto,
                onSharedPhotoHandled = onSharedPhotoHandled,
                onOpenEditor = { mode, uri -> nav.navigate(EditorRoute(mode.name, uri.toString())) },
                onOpenPlanner = { nav.navigate(PlannerRoute) },
                onOpenCovers = { nav.navigate(CoversRoute) },
                onOpenPro = upgrade,
                onOpenStudio = { nav.navigate(it) },
            )
        }
        composable<EditorRoute> {
            EditorScreen(onBack = back, onExport = { nav.navigate(ExportRoute) }, onUpgrade = upgrade)
        }
        composable<StudioRoute> {
            StudioScreen(onBack = back, onExport = { nav.navigate(ExportRoute) }, onUpgrade = upgrade)
        }
        composable<ExportRoute>(
            enterTransition = { slideInVertically(tween(DURATION)) { it / 4 } + fadeIn(ease) },
            popExitTransition = { slideOutVertically(tween(DURATION)) { it / 4 } + fadeOut(ease) },
        ) {
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
            enterTransition = { slideInVertically(tween(DURATION)) { it } },
            exitTransition = { fadeOut(tween(DURATION / 2)) },
            popExitTransition = { slideOutVertically(tween(DURATION)) { it } },
        ) {
            ProScreen(onClose = back)
        }
    }
}
