package dev.victorc.sunup.core.data

import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.morning.WeatherSource
import dev.victorc.sunup.core.domain.morning.WeatherToday
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.Foundation.timeIntervalSinceNow
import platform.darwin.NSObject

/**
 * The iOS twin of Android's MetNorwayWeatherSource: today's weather from MET Norway for the phone's approximate
 * location (Core Location, "while using the app"). Only the location rounded to about 1 km is
 * sent. Never on the ring path: "You're up!" asks for it after the alarm has stopped.
 */
class IosWeatherSource(private val clock: Clock) : WeatherSource {

    override suspend fun today(): Result<WeatherToday, DataError.Network> {
        val location = withContext(Dispatchers.Main) {
            val manager = CLLocationManager()
            val status = manager.authorizationStatus
            if (status != kCLAuthorizationStatusAuthorizedWhenInUse && status != kCLAuthorizationStatusAuthorizedAlways) {
                return@withContext Located.NotAllowed
            }
            approximateLocation(manager)?.let { Located.At(it.first, it.second) } ?: Located.Unknown
        }
        return when (location) {
            Located.NotAllowed -> Result.Error(DataError.Network.UNAUTHORIZED)
            Located.Unknown -> Result.Error(DataError.Network.NOT_FOUND)
            is Located.At -> fetch(MetNorwayForecast.url(location.latitude, location.longitude))
        }
    }

    /** The location the phone already knows if it's from the last hours, else a quick fresh one. On the main thread. */
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun approximateLocation(manager: CLLocationManager): Pair<Double, Double>? {
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
        val known = manager.location
        val fresh = if (known != null && -known.timestamp.timeIntervalSinceNow < FRESH_SECONDS) {
            known
        } else {
            withTimeoutOrNull(TIMEOUT_MILLIS) { oneLocation(manager) } ?: known
        }
        return fresh?.coordinate?.useContents { latitude to longitude }
    }

    private suspend fun oneLocation(manager: CLLocationManager): CLLocation? {
        // The manager holds its delegate weakly: this function keeps it alive until the answer.
        val delegate = OneLocation()
        manager.delegate = delegate
        try {
            return suspendCancellableCoroutine { continuation ->
                delegate.onResult = { location -> if (continuation.isActive) continuation.resume(location) }
                manager.requestLocation()
            }
        } finally {
            delegate.onResult = null
            manager.delegate = null
            manager.stopUpdatingLocation()
        }
    }

    private suspend fun fetch(url: String): Result<WeatherToday, DataError.Network> {
        val address = NSURL.URLWithString(url) ?: return Result.Error(DataError.Network.UNKNOWN)
        val request = NSMutableURLRequest.requestWithURL(address).apply {
            setTimeoutInterval(TIMEOUT_MILLIS / 1000.0)
            // MET Norway's terms: identify the app in the User-Agent.
            setValue(USER_AGENT, forHTTPHeaderField = "User-Agent")
        }
        return suspendCancellableCoroutine { continuation ->
            val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
                val result: Result<WeatherToday, DataError.Network> = when {
                    error != null || data == null -> Result.Error(DataError.Network.NO_INTERNET)
                    else -> when ((response as? NSHTTPURLResponse)?.statusCode?.toInt()) {
                        200 -> (NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?)
                            ?.let { MetNorwayForecast.parse(it, clock.now(), TimeZone.currentSystemDefault()) }
                            ?.let { Result.Success(it) }
                            ?: Result.Error(DataError.Network.SERIALIZATION)
                        429 -> Result.Error(DataError.Network.TOO_MANY_REQUESTS)
                        in 500..599 -> Result.Error(DataError.Network.SERVER_ERROR)
                        else -> Result.Error(DataError.Network.UNKNOWN)
                    }
                }
                if (continuation.isActive) continuation.resume(result)
            }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }

    private sealed interface Located {
        data object NotAllowed : Located
        data object Unknown : Located
        data class At(val latitude: Double, val longitude: Double) : Located
    }

    private companion object {
        // TODO: add a contact URL for the app once it has a website (MET Norway asks for one).
        const val USER_AGENT = "Sunup-iOS/0.1 (dev.victorc.sunup)"
        const val TIMEOUT_MILLIS = 6_000L
        const val FRESH_SECONDS = 6 * 60 * 60.0
    }
}

/** Answers one `requestLocation()`: the newest location, or null when Core Location fails. */
private class OneLocation : NSObject(), CLLocationManagerDelegateProtocol {
    var onResult: ((CLLocation?) -> Unit)? = null

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        onResult?.invoke(didUpdateLocations.lastOrNull() as? CLLocation)
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        onResult?.invoke(null)
    }
}
