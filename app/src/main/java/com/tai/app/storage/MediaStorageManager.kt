package com.tai.app.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.tai.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class SavedMediaResult(
    val uri: Uri,
    val file: File,
    val mimeType: String
)

class MediaStorageManager(private val context: Context) {

    private val folderName = "Tai"

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|\s]+"""), "_")
    }

    fun getTargetDirectory(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val taiDir = File(downloadsDir, folderName)
        if (!taiDir.exists()) {
            taiDir.mkdirs()
        }
        return taiDir
    }

    suspend fun saveMediaStream(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        contentLength: Long = -1L,
        onProgress: (Int) -> Unit = {}
    ): SavedMediaResult = withContext(Dispatchers.IO) {
        val safeName = sanitizeFileName(fileName)
        val targetDir = getTargetDirectory()
        val targetFile = File(targetDir, safeName)

        // Grava fisicamente no disco na pasta Download/Tai/
        FileOutputStream(targetFile).use { output ->
            copyStreamWithProgress(inputStream, output, contentLength, onProgress)
        }

        var resultUri: Uri? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$folderName")
                }
                resultUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } catch (e: Exception) {
                AppLogger.e("Storage", "Aviso ao registrar no MediaStore", e)
            }
        }

        // Garante indexação no MediaScanner para galeria e gerenciador
        MediaScannerConnection.scanFile(
            context,
            arrayOf(targetFile.absolutePath),
            arrayOf(mimeType)
        ) { path, scannedUri ->
            AppLogger.i("MediaScanner", "Arquivo indexado: $path -> $scannedUri")
        }

        val finalUri = resultUri ?: Uri.fromFile(targetFile)
        SavedMediaResult(uri = finalUri, file = targetFile, mimeType = mimeType)
    }

    suspend fun saveLocalFile(
        sourceFile: File,
        targetFileName: String,
        mimeType: String
    ): SavedMediaResult = withContext(Dispatchers.IO) {
        val safeName = sanitizeFileName(targetFileName)
        val targetDir = getTargetDirectory()
        val targetFile = File(targetDir, safeName)

        sourceFile.copyTo(targetFile, overwrite = true)

        var resultUri: Uri? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$folderName")
                }
                resultUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } catch (e: Exception) {
                AppLogger.e("Storage", "Aviso ao registrar no MediaStore", e)
            }
        }

        MediaScannerConnection.scanFile(
            context,
            arrayOf(targetFile.absolutePath),
            arrayOf(mimeType)
        ) { path, scannedUri ->
            AppLogger.i("MediaScanner", "Arquivo indexado: $path -> $scannedUri")
        }

        val finalUri = resultUri ?: Uri.fromFile(targetFile)
        SavedMediaResult(uri = finalUri, file = targetFile, mimeType = mimeType)
    }

    private fun copyStreamWithProgress(
        input: InputStream,
        output: java.io.OutputStream,
        totalBytes: Long,
        onProgress: (Int) -> Unit
    ) {
        val buffer = ByteArray(16 * 1024)
        var bytesRead: Int
        var count = 0L

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            count += bytesRead
            if (totalBytes > 0) {
                val percent = ((count * 100) / totalBytes).coerceIn(0, 100).toInt()
                onProgress(percent)
            }
        }
        output.flush()
    }
}
