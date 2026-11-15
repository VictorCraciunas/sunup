package dev.victorc.sunup.core.geo

import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.geo.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Where the map comes from. An interface so tests and previews can hand in a tiny atlas. */
fun interface GeoAtlasSource {
    suspend fun load(): Result<GeoAtlas, DataError.Local>
}

/**
 * Reads the map bundled with the app (no network) and keeps it in memory after the first load.
 * Not on the alarm's ring path: the sound is already playing before anything asks for a map.
 */
@OptIn(ExperimentalResourceApi::class)
class BundledGeoAtlasSource : GeoAtlasSource {

    private val mutex = Mutex()
    private var cached: GeoAtlas? = null

    override suspend fun load(): Result<GeoAtlas, DataError.Local> = mutex.withLock {
        cached?.let { return@withLock Result.Success(it) }
        try {
            val atlas = withContext(Dispatchers.IO) {
                GeoAtlas.parse(Res.readBytes(MAP_FILE).decodeToString())
            }
            if (atlas.shapes.isEmpty()) return@withLock Result.Error(DataError.Local.NOT_FOUND)
            cached = atlas
            Result.Success(atlas)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Missing file (map not built yet) or a broken one: the morning flow falls back.
            Result.Error(DataError.Local.NOT_FOUND)
        }
    }

    private companion object {
        const val MAP_FILE = "files/geo/countries.json"
    }
}
