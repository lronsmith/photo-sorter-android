package io.github.lronsmith.photosorter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.lronsmith.photosorter.AppDeps
import io.github.lronsmith.photosorter.ui.dupes.DuplicatesScreen
import io.github.lronsmith.photosorter.ui.dupes.DuplicatesViewModel
import io.github.lronsmith.photosorter.ui.settings.SettingsScreen
import io.github.lronsmith.photosorter.ui.settings.SettingsViewModel
import io.github.lronsmith.photosorter.ui.settings.TrashedScreen
import io.github.lronsmith.photosorter.ui.settings.TrashedViewModel
import io.github.lronsmith.photosorter.ui.start.StartScreen
import io.github.lronsmith.photosorter.ui.start.StartViewModel
import io.github.lronsmith.photosorter.ui.swipe.SwipeScreen
import io.github.lronsmith.photosorter.ui.swipe.SwipeViewModel
import io.github.lronsmith.photosorter.ui.theme.PsColors

private object Routes {
    const val START = "start"
    const val SWIPE = "swipe"
    const val DUPLICATES = "duplicates"
    const val SETTINGS = "settings"
    const val TRASHED = "trashed"
}

/**
 * The app's navigation: start → swipe / duplicates / settings → trashed. Each ViewModel is scoped to
 * its back-stack entry, so every visit to a screen gets a fresh one and leaving it clears it.
 * Navigation callbacks run only while their screen is resumed, so a double tap can't push or pop twice.
 */
@Composable
fun PhotoSorterApp(deps: AppDeps) {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = Routes.START,
        modifier = Modifier.fillMaxSize().background(PsColors.Background),
    ) {
        composable(Routes.START) {
            StartScreen(
                viewModel = viewModel {
                    StartViewModel(deps.media, deps.decisions, deps.settings, deps.trasher, deps::accessLevel)
                },
                onStart = dropUnlessResumed { nav.navigate(Routes.SWIPE) },
                onFindDuplicates = dropUnlessResumed { nav.navigate(Routes.DUPLICATES) },
                onOpenSettings = dropUnlessResumed { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SWIPE) {
            SwipeScreen(
                viewModel = viewModel {
                    SwipeViewModel(deps.media, deps.decisions, deps.settings, deps.trasher, deps.appScope)
                },
                onBack = dropUnlessResumed { nav.popBackStack() },
            )
        }
        composable(Routes.DUPLICATES) {
            DuplicatesScreen(
                viewModel = viewModel { DuplicatesViewModel(deps.media, deps.decisions, deps.trasher) },
                onBack = dropUnlessResumed { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel {
                    SettingsViewModel(deps.decisions, deps.trasher, deps::accessLevel, deps.sdkInt, deps.versionLabel)
                },
                onBack = dropUnlessResumed { nav.popBackStack() },
                onOpenTrashed = dropUnlessResumed { nav.navigate(Routes.TRASHED) },
            )
        }
        composable(Routes.TRASHED) {
            TrashedScreen(
                viewModel = viewModel { TrashedViewModel(deps.media, deps.decisions, deps.trasher) },
                onBack = dropUnlessResumed { nav.popBackStack() },
            )
        }
    }
}
