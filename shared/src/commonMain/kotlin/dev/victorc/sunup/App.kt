package dev.victorc.sunup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import dev.victorc.sunup.feature.decks.presentation.CardEditorRoot
import dev.victorc.sunup.feature.decks.presentation.DeckDetailRoot
import dev.victorc.sunup.feature.decks.presentation.DeckListRoot
import dev.victorc.sunup.feature.decks.presentation.GamePickerScreen
import dev.victorc.sunup.feature.decks.presentation.ImportRoot
import dev.victorc.sunup.feature.decks.presentation.NewDeckRoot
import kotlinx.serialization.Serializable

// Tabs (with the bottom bar)

@Serializable
private data object AlarmListRoute

@Serializable
private data object DeckListRoute

// Inner screens

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
 * Root of the app: the Alarm and Decks tabs, their inner screens, and (Android debug) the
 * test bench.
 *
 * @param testBench the debug test bench; when set, the alarm list links to it.
 */
@Composable
fun App(testBench: (@Composable () -> Unit)? = null) {
    SunupTheme {
        val navController = rememberNavController()
        val entry by navController.currentBackStackEntryAsState()
        val destination = entry?.destination
        val onAlarms = destination?.hasRoute<AlarmListRoute>() == true
        val onDecks = destination?.hasRoute<DeckListRoute>() == true
        val showBar = onAlarms || onDecks

        Column(Modifier.fillMaxSize().background(SunupTheme.colors.background)) {
            Box(
                Modifier
                    .weight(1f)
                    // The bar takes the system navigation area; screens above it mustn't pad for it again.
                    .then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
            ) {
                SunupNavHost(navController, testBench)
            }
            if (showBar) {
                SunupNavBar(
                    items = listOf(
                        NavBarItem("Alarm", NavIcon.ALARM, selected = onAlarms, onClick = { navController.switchTab(AlarmListRoute) }),
                        NavBarItem("Decks", NavIcon.DECKS, selected = onDecks, onClick = { navController.switchTab(DeckListRoute) }),
                    ),
                )
            }
        }
    }
}

@Composable
private fun SunupNavHost(navController: NavHostController, testBench: (@Composable () -> Unit)?) {
    NavHost(navController = navController, startDestination = AlarmListRoute) {
        composable<AlarmListRoute> {
            AlarmListRoot(
                onOpenEditor = { id -> navController.navigate(AlarmEditorRoute(id)) },
                onOpenTestBench = testBench?.let { { navController.navigate(TestBenchRoute) } },
            )
        }
        composable<AlarmEditorRoute> { entry ->
            val route = entry.toRoute<AlarmEditorRoute>()
            AlarmEditorRoot(alarmId = route.alarmId, onClose = { navController.popBackStack() })
        }

        composable<DeckListRoute> {
            DeckListRoot(
                onOpenDeck = { id -> navController.navigate(DeckRoute(id)) },
                onNewDeck = { navController.navigate(NewDeckRoute) },
                onImport = { navController.navigate(ImportRoute) },
            )
        }
        composable<ImportRoute> {
            ImportRoot(
                // The new deck replaces this screen, so Back goes to the deck list.
                onCreated = { id ->
                    navController.navigate(DeckRoute(id)) { popUpTo<ImportRoute> { inclusive = true } }
                },
                onClose = { navController.popBackStack() },
            )
        }
        composable<NewDeckRoute> {
            NewDeckRoot(
                // The new deck replaces this screen, so Back goes to the deck list.
                onCreated = { id ->
                    navController.navigate(DeckRoute(id)) { popUpTo<NewDeckRoute> { inclusive = true } }
                },
                onClose = { navController.popBackStack() },
            )
        }
        composable<DeckRoute> { entry ->
            val route = entry.toRoute<DeckRoute>()
            DeckDetailRoot(
                deckId = route.deckId,
                onAddCard = { id -> navController.navigate(GamePickerRoute(id)) },
                onOpenCard = { id, game, cardId -> navController.navigate(CardRoute(id, game.name, cardId)) },
                onClose = { navController.popBackStack() },
            )
        }
        composable<GamePickerRoute> { entry ->
            val route = entry.toRoute<GamePickerRoute>()
            GamePickerScreen(
                onPick = { game -> navController.navigate(CardRoute(route.deckId, game.name)) },
                onBack = { navController.popBackStack() },
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
                onChangeGame = { navController.popBackStack() },
                // Done: back to the deck, past the game picker.
                onClose = {
                    if (!navController.popBackStack<DeckRoute>(inclusive = false)) navController.popBackStack()
                },
            )
        }

        composable<TestBenchRoute> {
            TestBenchScreen(onBack = { navController.popBackStack() }) { testBench?.invoke() }
        }
    }
}

/** Bottom bar tabs: one copy of each tab, and the tab keeps its place when you come back. */
private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// TODO(M4): remove with the test bench once onboarding exists.
@Composable
private fun TestBenchScreen(onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SunupTheme.colors.background)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(SunupTheme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
    ) {
        TextButton(onClick = onBack) { Text("‹ Alarms", color = SunupTheme.colors.indigo) }
        content()
    }
}
