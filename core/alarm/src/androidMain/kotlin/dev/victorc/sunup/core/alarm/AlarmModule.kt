package dev.victorc.sunup.core.alarm

import android.content.Context
import androidx.core.util.AtomicFile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Long-lived scope for work that outlives a broadcast, e.g. logging after the alarm started. */
class AlarmScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)

val alarmModule = module {
    single { AlarmScope() }
    single<AlarmStore> { FileAlarmStore(AndroidStateFile(alarmStoreFile(androidContext()))) }
    single<PlatformAlarmClock> { AndroidPlatformAlarmClock(androidContext()) }
    single<AlarmScheduler> { DefaultAlarmScheduler(store = get(), platform = get()) }
    single { AlarmPermissions(androidContext()) }
}

/** Device-protected storage can be read after a reboot, before the phone is unlocked. */
private fun alarmStoreFile(context: Context): File =
    File(context.createDeviceProtectedStorageContext().filesDir, "alarms.json")

/** Writes go to a temporary file first, so a crash mid-write never leaves a broken file. */
internal class AndroidStateFile(file: File) : StateFile {

    private val atomicFile = AtomicFile(file)

    override fun read(): String? =
        if (atomicFile.baseFile.exists()) atomicFile.readFully().decodeToString() else null

    override fun write(text: String) {
        atomicFile.baseFile.parentFile?.mkdirs()
        val stream = atomicFile.startWrite()
        try {
            stream.write(text.encodeToByteArray())
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            throw e
        }
    }
}
