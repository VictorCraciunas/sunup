package dev.victorc.sunup.feature.decks.presentation

import androidx.compose.runtime.Composable

/**
 * Opens the system file picker for a text or JSON file and hands back its contents, or
 * null if nothing was picked or it couldn't be read. Returns the function that opens it,
 * or null on a platform that can't (the Import screen then offers pasting only).
 */
@Composable
internal expect fun rememberTextFilePicker(onPicked: (String?) -> Unit): (() -> Unit)?

/** Files bigger than this aren't decks; reading stops there. */
internal const val MAX_IMPORT_FILE_BYTES = 2 * 1024 * 1024
