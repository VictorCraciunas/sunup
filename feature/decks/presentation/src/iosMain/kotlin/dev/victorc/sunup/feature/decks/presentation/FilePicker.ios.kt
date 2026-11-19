package dev.victorc.sunup.feature.decks.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

/**
 * Apple's Files picker (on the phone, iCloud Drive and other file apps). It hands Sunup a copy
 * of the file, so no extra access is needed to read it.
 */
@Composable
internal actual fun rememberTextFilePicker(onPicked: (String?) -> Unit): (() -> Unit)? {
    val picked by rememberUpdatedState(onPicked)
    val screen = LocalUIViewController.current
    // The picker holds its delegate weakly: remembered here so it stays alive while the picker is open.
    val delegate = remember { PickerDelegate { text -> picked(text) } }
    return remember(screen, delegate) {
        {
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeJSON, UTTypePlainText),
                asCopy = true,
            )
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            // Shown over whatever is on top (Sunup's screen, or a sheet above it).
            var top = screen
            while (top.presentedViewController != null) top = top.presentedViewController!!
            top.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class PickerDelegate(private val onPicked: (String?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onPicked((didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(::readCapped))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPicked(null)
    }
}

/** The file as text, or null if it can't be read or is bigger than [MAX_IMPORT_FILE_BYTES]. */
private fun readCapped(url: NSURL): String? {
    // Only JSON and text files can be picked, so reading it whole before checking the size is fine.
    val data = NSData.dataWithContentsOfURL(url) ?: return null
    if (data.length.toLong() > MAX_IMPORT_FILE_BYTES) return null
    return NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
}
