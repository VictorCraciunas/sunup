package dev.victorc.sunup.feature.alarms.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.EventKit.EKAuthorizationStatusFullAccess
import platform.EventKit.EKAuthorizationStatusNotDetermined
import platform.EventKit.EKEntityTypeEvent
import platform.EventKit.EKEventStore
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Apple's prompts for approximate location ("while using the app") and full calendar access.
 * Apple asks only once: after a "Don't Allow", only the Settings app can turn it on, so the
 * answer is false and Home says so.
 */
@Composable
actual fun rememberMorningPermissions(): MorningPermissions {
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }
    val location = remember { LocationPrompt() }
    val locationAllowed = remember(refresh) { location.allowed() }
    val calendarAllowed = remember(refresh) { calendarAllowed() }

    return MorningPermissions(
        locationAllowed = locationAllowed,
        calendarAllowed = calendarAllowed,
        askLocation = { onResult ->
            location.ask { allowed ->
                refresh++
                onResult(allowed)
            }
        },
        askCalendar = { onResult ->
            askCalendar { allowed ->
                refresh++
                onResult(allowed)
            }
        },
    )
}

private fun calendarAllowed(): Boolean =
    EKEventStore.authorizationStatusForEntityType(EKEntityTypeEvent) == EKAuthorizationStatusFullAccess

/** [onResult] runs on the main thread. */
private fun askCalendar(onResult: (Boolean) -> Unit) {
    if (EKEventStore.authorizationStatusForEntityType(EKEntityTypeEvent) != EKAuthorizationStatusNotDetermined) {
        onResult(calendarAllowed())
        return
    }
    eventStore.requestFullAccessToEventsWithCompletion { granted, _ ->
        dispatch_async(dispatch_get_main_queue()) { onResult(granted) }
    }
}

/** One store for the app's lifetime, as Apple recommends; it must outlive the prompt. */
private val eventStore by lazy { EKEventStore() }

/** Owns the location manager (and is its delegate) so Apple's answer comes back here, on the main thread. */
private class LocationPrompt : NSObject(), CLLocationManagerDelegateProtocol {
    private val manager = CLLocationManager()
    private var pending: ((Boolean) -> Unit)? = null

    init {
        manager.delegate = this
    }

    fun allowed(): Boolean = manager.authorizationStatus.let {
        it == kCLAuthorizationStatusAuthorizedWhenInUse || it == kCLAuthorizationStatusAuthorizedAlways
    }

    fun ask(onResult: (Boolean) -> Unit) {
        if (manager.authorizationStatus != kCLAuthorizationStatusNotDetermined) {
            onResult(allowed())
            return
        }
        pending = onResult
        manager.requestWhenInUseAuthorization()
    }

    // Also called once right after the manager is made; nothing is pending then.
    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        if (manager.authorizationStatus == kCLAuthorizationStatusNotDetermined) return
        pending?.invoke(allowed())
        pending = null
    }
}
