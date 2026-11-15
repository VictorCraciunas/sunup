package dev.victorc.sunup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.feature.alarms.presentation.AlarmEditorRoot
import dev.victorc.sunup.feature.alarms.presentation.AlarmListRoot
import kotlinx.serialization.Serializable

@Serializable
private data object AlarmListRoute

/** Null [alarmId]: a new alarm. */
@Serializable
private data class AlarmEditorRoute(val alarmId: String? = null)

@Serializable
private data object TestBenchRoute

/**
 * Root of the app: the alarm list, the alarm editor and (Android debug) the test bench.
 *
 * @param testBench the debug test bench; when set, the alarm list links to it.
 */
@Composable
fun App(testBench: (@Composable () -> Unit)? = null) {
    SunupTheme {
        val navController = rememberNavController()
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
            composable<TestBenchRoute> {
                TestBenchScreen(onBack = { navController.popBackStack() }) { testBench?.invoke() }
            }
        }
    }
}

// TODO(M4): remove with the test bench once onboarding and the deck screens exist.
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
