package com.jarvis.robot

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Share and Download for the picture of the Camera button (full-screen view, classic and v2 layouts).
 * The bytes are the JPEG exactly as the robot sent it, so nothing is compressed a second time.
 */
object PhotoShare {
    private fun stamp() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    /** Android's share sheet (mail, WhatsApp, Drive, ...): the picture goes to the chosen app as an attachment. */
    fun share(context: Context, bytes: ByteArray) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val old = System.currentTimeMillis() - 60 * 60 * 1000L
        dir.listFiles()?.forEach { if (it.lastModified() < old) it.delete() }   // copies handed out more than an hour ago
        val file = File(dir, "jarvis-${stamp()}.jpg")
        val uri = runCatching {
            file.writeBytes(bytes)
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        }.getOrNull()
        if (uri == null) {
            Toast.makeText(context, tr("The picture could not be saved"), Toast.LENGTH_LONG).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, tr("Share picture")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(chooser) }
            .onFailure { Toast.makeText(context, tr("No app to share with"), Toast.LENGTH_LONG).show() }
    }

    /** Saves the picture in the phone's gallery (Pictures/Jarvis) and says so. */
    fun download(context: Context, bytes: ByteArray) {
        val name = "jarvis-${stamp()}.jpg"
        val message = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Jarvis")
                    put(MediaStore.Images.Media.IS_PENDING, 1)   // hidden from the gallery until the file is complete
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no gallery entry")
                try {
                    resolver.openOutputStream(uri)!!.use { it.write(bytes) }
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                tr("Saved to Pictures/Jarvis")
            } else {
                // Android 8 and 9 want a storage permission for the public Pictures folder: the app's own Pictures folder needs none
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Jarvis").apply { mkdirs() }
                File(dir, name).writeBytes(bytes)
                tr("Saved in the app's Pictures/Jarvis folder")
            }
        }.getOrElse { tr("The picture could not be saved") }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
