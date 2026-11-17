package dev.victorc.sunup.ios

import dev.victorc.sunup.core.alarm.StateFile
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/** The alarm engine's JSON file. Written whole and atomically, like Android's AtomicFile. */
class IosStateFile(private val path: String) : StateFile {

    @OptIn(ExperimentalForeignApi::class)
    override fun read(): String? =
        if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
            NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
        } else {
            null
        }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun write(text: String) {
        val ok = NSString.create(string = text).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        check(ok) { "Couldn't write $path" }
    }
}

/** Application Support: the app's own files, kept out of the user's Files app. */
fun applicationSupportPath(file: String): String = directory(NSApplicationSupportDirectory) + "/" + file

fun documentsPath(file: String): String = directory(NSDocumentDirectory) + "/" + file

@OptIn(ExperimentalForeignApi::class)
private fun directory(kind: NSSearchPathDirectory): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = kind,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(url?.path) { "No app directory" }
}
