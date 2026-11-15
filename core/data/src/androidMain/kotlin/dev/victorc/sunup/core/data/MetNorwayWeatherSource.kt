package dev.victorc.sunup.core.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.morning.WeatherSource
import dev.victorc.sunup.core.domain.morning.WeatherToday
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone

/**
 * Today's weather from MET Norway (free, no key, "Weather data from MET Norway") for the
 * phone's approximate location. Only the location rounded to about 1 km is sent. Never on
 * the ring path: "You're up!" asks for it after the alarm has stopped.
 */
class MetNorwayWeatherSource(
    private val context: Context,
    private val clock: Clock,
) : WeatherSource {

    override suspend fun today(): Result<WeatherToday, DataError.Network> = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return@withContext Result.Error(DataError.Network.UNAUTHORIZED)
        }
        val location = approximateLocation() ?: return@withContext Result.Error(DataError.Network.NOT_FOUND)
        // Two decimals: about 1 km. Enough for the weather, and MET asks for no more than four.
        val url = String.format(Locale.US, FORECAST_URL, location.latitude, location.longitude)
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                // MET Norway's terms: identify the app in the User-Agent.
                connection.setRequestProperty("User-Agent", USER_AGENT)
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        MetNorwayForecast.parse(body, clock.now(), TimeZone.currentSystemDefault())
                            ?.let { Result.Success(it) }
                            ?: Result.Error(DataError.Network.SERIALIZATION)
                    }
                    429 -> Result.Error(DataError.Network.TOO_MANY_REQUESTS)
                    in 500..599 -> Result.Error(DataError.Network.SERVER_ERROR)
                    else -> Result.Error(DataError.Network.UNKNOWN)
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            Result.Error(DataError.Network.NO_INTERNET)
        }
    }

    /** The newest location the phone already knows; if there's none from the last hours, a quick fresh one. */
    @SuppressLint("MissingPermission") // Checked in today().
    private suspend fun approximateLocation(): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val known = PROVIDERS
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        if (known != null && System.currentTimeMillis() - known.time < FRESH_MILLIS) return known

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        ) {
            val fresh = withTimeoutOrNull(TIMEOUT_MILLIS.toLong()) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    runCatching {
                        manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, signal, context.mainExecutor) { location ->
                            if (continuation.isActive) continuation.resume(location)
                        }
                    }.onFailure { if (continuation.isActive) continuation.resume(null) }
                }
            }
            if (fresh != null) return fresh
        }
        return known
    }

    private companion object {
        const val FORECAST_URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.2f&lon=%.2f"
        // TODO: add a contact URL for the app once it has a website (MET Norway asks for one).
        const val USER_AGENT = "Sunup-Android/0.1 (dev.victorc.sunup)"
        const val TIMEOUT_MILLIS = 6_000
        const val FRESH_MILLIS = 6 * 60 * 60 * 1000L
        // "fused" is Android 12+; GPS needs the precise-location permission, so it usually fails quietly.
        val PROVIDERS = listOf(LocationManager.NETWORK_PROVIDER, "fused", LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
    }
}
