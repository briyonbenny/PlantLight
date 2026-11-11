package ie.griffith.plantlight.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Photos of saved spots live in the app's private files directory.
 * They are never uploaded and are exposed to the camera / share sheet
 * only through a FileProvider URI.
 */
class PhotoStore(private val context: Context) {

    private val photosDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun newPhotoFile(): File =
        File(photosDir, "spot_${System.currentTimeMillis()}.jpg")

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

    fun uriForPath(path: String): Uri = uriFor(File(path))

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }
}
