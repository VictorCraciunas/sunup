package dev.victorc.sunup.feature.onboarding.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

/** The whole first-run flow, as one nested graph. App navigates here when onboarding isn't done. */
@Serializable
data object OnboardingRoute

@Serializable
private data object WelcomeStep

@Serializable
private data object GoalStep

@Serializable
private data object TimeStep

@Serializable
private data object PromiseStep

@Serializable
private data object PermissionsStep

@Serializable
private data object ModeStep

/**
 * Welcome → what to learn → rise time → promise → permissions → wake-up mode. Back goes one
 * screen back. All screens share one [OnboardingViewModel], kept for as long as the flow is open.
 *
 * @param onFinished the first alarm is saved; [openDecks] when the user wants to make cards first
 */
fun NavGraphBuilder.onboardingGraph(navController: NavHostController, onFinished: (openDecks: Boolean) -> Unit) {
    navigation<OnboardingRoute>(startDestination = WelcomeStep) {
        composable<WelcomeStep> { entry ->
            WelcomeScreen(onStart = { navController.from(entry) { navigate(GoalStep) } })
        }
        composable<GoalStep> { entry ->
            val viewModel = entry.onboardingViewModel(navController)
            val state by viewModel.state.collectAsStateWithLifecycle()
            GoalScreen(
                state,
                viewModel::onAction,
                onBack = { navController.from(entry) { popBackStack() } },
                onContinue = { navController.from(entry) { navigate(TimeStep) } },
            )
        }
        composable<TimeStep> { entry ->
            val viewModel = entry.onboardingViewModel(navController)
            val state by viewModel.state.collectAsStateWithLifecycle()
            TimeScreen(
                state,
                viewModel::onAction,
                onBack = { navController.from(entry) { popBackStack() } },
                onContinue = { navController.from(entry) { navigate(PromiseStep) } },
            )
        }
        composable<PromiseStep> { entry ->
            val viewModel = entry.onboardingViewModel(navController)
            val state by viewModel.state.collectAsStateWithLifecycle()
            PromiseScreen(
                state,
                viewModel::onAction,
                onBack = { navController.from(entry) { popBackStack() } },
                onContinue = { navController.from(entry) { navigate(PermissionsStep) } },
            )
        }
        composable<PermissionsStep> { entry ->
            val viewModel = entry.onboardingViewModel(navController)
            val state by viewModel.state.collectAsStateWithLifecycle()
            PermissionsScreen(
                state,
                viewModel::onAction,
                onBack = { navController.from(entry) { popBackStack() } },
                onContinue = { navController.from(entry) { navigate(ModeStep) } },
            )
        }
        composable<ModeStep> { entry ->
            val viewModel = entry.onboardingViewModel(navController)
            val state by viewModel.state.collectAsStateWithLifecycle()
            val finished by rememberUpdatedState(onFinished)
            LaunchedEffect(viewModel) {
                viewModel.events.collect { event ->
                    when (event) {
                        is OnboardingEvent.Finished -> finished(event.openDecks)
                    }
                }
            }
            ModeScreen(state, viewModel::onAction, onBack = { navController.from(entry) { popBackStack() } })
        }
    }
}

/**
 * Runs [action] only while [entry]'s screen is still the one in front. Once the first tap has
 * started opening another screen (or going back), it no longer is, so a fast second tap on the
 * screen that's sliding away can't open a screen twice or go back two screens.
 */
private inline fun NavHostController.from(entry: NavBackStackEntry, action: NavHostController.() -> Unit) {
    if (currentBackStackEntry?.id == entry.id) action()
}

/** The ViewModel lives on the graph's own back stack entry, so every step gets the same one. */
@Composable
private fun NavBackStackEntry.onboardingViewModel(navController: NavHostController): OnboardingViewModel {
    val graphEntry = remember(this) { navController.getBackStackEntry<OnboardingRoute>() }
    return koinViewModel(viewModelStoreOwner = graphEntry)
}
