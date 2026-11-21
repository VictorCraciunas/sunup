package dev.victorc.sunup

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.victorc.sunup.core.designsystem.components.NavBarItem
import dev.victorc.sunup.core.designsystem.components.NavIcon
import dev.victorc.sunup.core.designsystem.components.SunupNavBar
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.feature.alarms.presentation.AlarmEditorRoot
import dev.victorc.sunup.feature.alarms.presentation.AlarmListRoot
import dev.victorc.sunup.feature.alarms.presentation.HomeRoot
import dev.victorc.sunup.feature.decks.presentation.CardEditorRoot
import dev.victorc.sunup.feature.decks.presentation.DeckDetailRoot
import dev.victorc.sunup.feature.decks.presentation.DeckListRoot
import dev.victorc.sunup.feature.decks.presentation.GamePickerScreen
import dev.victorc.sunup.feature.decks.presentation.ImportRoot
import dev.victorc.sunup.feature.decks.presentation.NewDeckRoot
import dev.victorc.sunup.feature.onboarding.presentation.OnboardingRoute
import dev.victorc.sunup.feature.onboarding.presentation.onboardingGraph
import dev.victorc.sunup.feature.onboarding.presentation.rememberOnboardingDone
import dev.victorc.sunup.feature.progress.presentation.ProgressRoot
import kotlinx.serialization.Serializable

// Tabs (with the bottom bar)

/** The Alarm tab: Home. */
@Serializable
private data object HomeRoute

@Serializable
private data object DeckListRoute

@Serializable
private data object ProgressRoute

// Inner screens

@Serializable
private data object AlarmListRoute

/** Null [alarmId]: a new alarm. */
@Serializable
private data class AlarmEditorRoute(val alarmId: String? = null)

@Serializable
private data object NewDeckRoute

@Serializable
private data object ImportRoute

@Serializable
private data class DeckRoute(val deckId: String)

@Serializable
private data class GamePickerRoute(val deckId: String)

/** [game] is a Game name; null [cardId] makes a new card. */
@Serializable
private data class CardRoute(val deckId: String, val game: String, val cardId: String? = null)

@Serializable
private data object TestBenchRoute

/**
 * Root of the app: onboarding the first time, then the Alarm tab (Home) and the Decks tab,
 * their inner screens, and (Android debug) the test bench.
 *
 * @param testBench the debug test bench; when set, the alarm list links to it.
 * @param onPreviewMorning plays an alarm's morning as practice (Android: the ringing screen); null hides Home's preview button
 */
@Composable
fun App(
    testBench: (@Composable () -> Unit)? = null,
    onPreviewMorning: ((alarmId: String) -> Unit)? = null,
) {
    SunupTheme {
        // Until the settings file is read (a moment), just the background, so neither the
        // onboarding nor the alarm list flashes up by mistake.
        when (val onboardingDone = rememberOnboardingDone()) {
            null -> Box(Modifier.fillMaxSize().background(SunupTheme.colors.background))
            else -> {
                // Decided once: finishing onboarding navigates away by itself.
                val start: Any = remember { if (onboardingDone) HomeRoute else OnboardingRoute }
                SunupRoot(start, testBench, onPreviewMorning)
            }
        }
    }
}

@Composable
private fun SunupRoot(start: Any, testBench: (@Composable () -> Unit)?, onPreviewMorning: ((String) -> Unit)?) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val onHome = destination?.hasRoute<HomeRoute>() == true
    val onDecks = destination?.hasRoute<DeckListRoute>() == true
    val onProgress = destination?.hasRoute<ProgressRoute>() == true
    val showBar = onHome || onDecks || onProgress

    Column(Modifier.fillMaxSize().background(SunupTheme.colors.background)) {
        Box(
            Modifier
                .weight(1f)
                // The bar takes the system navigation area; screens above it mustn't pad for it again.
                .then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
        ) {
            SunupNavHost(navController, start, testBench, onPreviewMorning)
        }
        if (showBar) {
            SunupNavBar(
                items = listOf(
                    NavBarItem("Alarm", NavIcon.ALARM, selected = onHome, onClick = { navController.switchTab(HomeRoute) }),
                    NavBarItem("Decks", NavIcon.DECKS, selected = onDecks, onClick = { navController.switchTab(DeckListRoute) }),
                    NavBarItem("Progress", NavIcon.PROGRESS, selected = onProgress, onClick = { navController.switchTab(ProgressRoute) }),
                ),
            )
        }
    }
}

@Composable
private fun SunupNavHost(
    navController: NavHostController,
    start: Any,
    testBench: (@Composable () -> Unit)?,
    onPreviewMorning: ((String) -> Unit)?,
) {
    NavHost(navController = navController, startDestination = start) {
        onboardingGraph(navController) { openDecks ->
            // Onboarding is gone from the back stack: Back from Home leaves the app.
            navController.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } }
            if (openDecks) navController.switchTab(DeckListRoute)
        }

        composable<HomeRoute>(
            enterTransition = tabEnter,
            exitTransition = tabExit,
            popEnterTransition = tabEnter,
            popExitTransition = tabExit,
        ) { entry ->
            HomeRoot(
                onOpenAlarms = { navController.from(entry) { navigate(AlarmListRoute) } },
                onNewAlarm = { navController.from(entry) { navigate(AlarmEditorRoute()) } },
                onOpenDeck = { id -> navController.from(entry) { navigate(DeckRoute(id)) } },
                onOpenDecks = { navController.switchTab(DeckListRoute) },
                onPreview = onPreviewMorning,
            )
        }
        composable<AlarmListRoute> { entry ->
            AlarmListRoot(
                onOpenEditor = { id -> navController.from(entry) { navigate(AlarmEditorRoute(id)) } },
                onBack = { navController.from(entry) { popBackStack() } },
                onOpenTestBench = testBench?.let { { navController.from(entry) { navigate(TestBenchRoute) } } },
            )
        }
        composable<AlarmEditorRoute> { entry ->
            val route = entry.toRoute<AlarmEditorRoute>()
            AlarmEditorRoot(alarmId = route.alarmId, onClose = { navController.from(entry) { popBackStack() } })
        }

        composable<ProgressRoute>(
            enterTransition = tabEnter,
            exitTransition = tabExit,
            popEnterTransition = tabEnter,
            popExitTransition = tabExit,
        ) {
            ProgressRoot()
        }

        composable<DeckListRoute>(
            enterTransition = tabEnter,
            exitTransition = tabExit,
            popEnterTransition = tabEnter,
            popExitTransition = tabExit,
        ) { entry ->
            DeckListRoot(
                onOpenDeck = { id -> navController.from(entry) { navigate(DeckRoute(id)) } },
                onNewDeck = { navController.from(entry) { navigate(NewDeckRoute) } },
                onImport = { navController.from(entry) { navigate(ImportRoute) } },
            )
        }
        composable<ImportRoute> { entry ->
            ImportRoot(
                // The new deck replaces this screen, so Back goes to the deck list.
                onCreated = { id ->
                    navController.from(entry) { navigate(DeckRoute(id)) { popUpTo<ImportRoute> { inclusive = true } } }
                },
                onClose = { navController.from(entry) { popBackStack() } },
            )
        }
        composable<NewDeckRoute> { entry ->
            NewDeckRoot(
                // The new deck replaces this screen, so Back goes to the deck list.
                onCreated = { id ->
                    navController.from(entry) { navigate(DeckRoute(id)) { popUpTo<NewDeckRoute> { inclusive = true } } }
                },
                onClose = { navController.from(entry) { popBackStack() } },
            )
        }
        composable<DeckRoute> { entry ->
            val route = entry.toRoute<DeckRoute>()
            DeckDetailRoot(
                deckId = route.deckId,
                onAddCard = { id -> navController.from(entry) { navigate(GamePickerRoute(id)) } },
                onOpenCard = { id, game, cardId -> navController.from(entry) { navigate(CardRoute(id, game.name, cardId)) } },
                onClose = { navController.from(entry) { popBackStack() } },
            )
        }
        composable<GamePickerRoute> { entry ->
            val route = entry.toRoute<GamePickerRoute>()
            GamePickerScreen(
                onPick = { game -> navController.from(entry) { navigate(CardRoute(route.deckId, game.name)) } },
                onBack = { navController.from(entry) { popBackStack() } },
            )
        }
        composable<CardRoute> { entry ->
            val route = entry.toRoute<CardRoute>()
            val game = Game.entries.firstOrNull { it.name == route.game } ?: Game.PICK_MEANING
            CardEditorRoot(
                deckId = route.deckId,
                game = game,
                cardId = route.cardId,
                // Back to the game picker, which is right behind a new card.
                onChangeGame = { navController.from(entry) { popBackStack() } },
                // Done: back to the deck, past the game picker.
                onClose = {
                    navController.from(entry) { if (!popBackStack<DeckRoute>(inclusive = false)) popBackStack() }
                },
            )
        }

        composable<TestBenchRoute> { entry ->
            TestBenchScreen(onBack = { navController.from(entry) { popBackStack() } }) { testBench?.invoke() }
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

/**
 * Bottom bar tabs: one copy of each tab, and the tab keeps its place when you come back.
 * Home is always the bottom of the stack (the graph's start can be onboarding).
 */
private fun NavBackStackEntry.isTab(): Boolean =
    destination.hasRoute<HomeRoute>() || destination.hasRoute<DeckListRoute>() || destination.hasRoute<ProgressRoute>()

/**
 * Tab to tab is a quick fade, like a tab bar. Without this, iOS slides the new tab in like a
 * new page (half a second, the old one darkening), and Android fades slowly. Opening a screen
 * from a tab returns null here, so it keeps the platform's own page transition.
 */
private val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? =
    { if (initialState.isTab()) fadeIn(tween(TAB_FADE_MILLIS)) else null }

private val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? =
    { if (targetState.isTab()) fadeOut(tween(TAB_FADE_MILLIS)) else null }

private const val TAB_FADE_MILLIS = 120

private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo<HomeRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// Debug only: MainActivity passes the test bench. TODO: drop it before the first release.
@Composable
private fun TestBenchScreen(onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SunupTheme.colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(SunupTheme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
    ) {
        TextButton(onClick = onBack) { Text("‹ Back", color = SunupTheme.colors.indigo) }
        content()
    }
}
