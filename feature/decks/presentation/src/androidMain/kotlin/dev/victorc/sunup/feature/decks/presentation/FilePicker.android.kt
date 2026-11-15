package dev.victorc.sunup.feature.decks.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

@Composable
internal actual fun rememberTextFilePicker(onPicked: (String?) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val picked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            picked(null)
            return@rememberLauncherForActivityResult
        }
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream -> readCapped(stream) }
        }.getOrNull()
        picked(text)
    }
    // Many file managers label .json files as plain text or "unknown", so allow those too.
    return { launcher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
}

/** The file as text, or null if it's bigger than [MAX_IMPORT_FILE_BYTES]. Works on every Android version. */
private fun readCapped(stream: InputStream): String? {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    while (true) {
        val read = stream.read(chunk)
        if (read < 0) break
        if (out.size() + read > MAX_IMPORT_FILE_BYTES) return null
        out.write(chunk, 0, read)
    }
    return out.toByteArray().decodeToString()
}
