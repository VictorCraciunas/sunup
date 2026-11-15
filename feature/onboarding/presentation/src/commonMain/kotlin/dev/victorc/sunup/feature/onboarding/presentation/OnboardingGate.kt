package dev.victorc.sunup.feature.onboarding.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.victorc.sunup.core.domain.settings.AppSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel

class OnboardingGateViewModel(settings: AppSettingsRepository) : ViewModel() {
    /** Null until the settings file has been read. */
    val done: StateFlow<Boolean?> = settings.observeOnboardingDone()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
}

/** Whether the first-run screens were finished; null for the moment it takes to find out. */
@Composable
fun rememberOnboardingDone(): Boolean? {
    val viewModel = koinViewModel<OnboardingGateViewModel>()
    val done by viewModel.done.collectAsStateWithLifecycle()
    return done
}
