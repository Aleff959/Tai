package com.tai.app.ota

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class OtaStatus {
    object Idle : OtaStatus()
    object Checking : OtaStatus()
    object UpToDate : OtaStatus()
    data class UpdateAvailable(
        val version: String,
        val title: String,
        val changelog: String,
        val downloadUrl: String,
        val assetName: String
    ) : OtaStatus()
    data class Downloading(val progressPercent: Int) : OtaStatus()
    data class ReadyToInstall(val apkFile: File) : OtaStatus()
    data class Error(val message: String) : OtaStatus()
}

class OtaUpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Configurable repo: defaults to Aleff959/Tai
    var repoOwner: String = "Aleff959"
    var repoName: String = "Tai"

    private val _status = MutableStateFlow<OtaStatus>(OtaStatus.Idle)
    val status: StateFlow<OtaStatus> = _status.asStateFlow()

    fun resetStatus() {
        _status.value = OtaStatus.Idle
    }

    suspend fun checkForUpdates(currentVersion: String = "0.0.1", currentComp: Int = 1) = withContext(Dispatchers.IO) {
        _status.value = OtaStatus.Checking
        try {
            val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Tai-Android-App")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    _status.value = OtaStatus.UpToDate
                } else {
                    _status.value = OtaStatus.Error("Erro ao buscar atualizações (HTTP ${response.code})")
                }
                return@withContext
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").trim()
            val releaseName = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "Sem notas de atualização.")
            val assets = json.optJSONArray("assets")

            var apkUrl: String? = null
            var apkName: String = "Tai_update.apk"

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkName = name
                        break
                    }
                }
            }

            if (apkUrl.isNullOrEmpty()) {
                _status.value = OtaStatus.Error("Nenhum arquivo APK anexado ao release.")
                return@withContext
            }

            val cleanTag = tagName.removePrefix("v").trim()
            val hasNewVersion = isVersionNewer(cleanTag, currentVersion, currentComp)

            if (hasNewVersion) {
                _status.value = OtaStatus.UpdateAvailable(
                    version = cleanTag,
                    title = releaseName,
                    changelog = releaseNotes,
                    downloadUrl = apkUrl,
                    assetName = apkName
                )
                showUpdateNotification(releaseName, "Nova versão disponível: $releaseName. Toque para atualizar.")
            } else {
                _status.value = OtaStatus.UpToDate
            }

        } catch (e: Exception) {
            _status.value = OtaStatus.Error(e.message ?: "Falha ao verificar atualizações")
        }
    }

    suspend fun downloadAndPrepareInstall(downloadUrl: String, fileName: String) = withContext(Dispatchers.IO) {
        _status.value = OtaStatus.Downloading(0)
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Tai-Android-App")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                _status.value = OtaStatus.Error("Falha no download (HTTP ${response.code})")
                return@withContext
            }

            val responseBody = response.body ?: throw Exception("Corpo da resposta vazio")
            val contentLength = responseBody.contentLength()

            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outputFile = File(updateDir, fileName)

            responseBody.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val percent = ((totalRead * 100) / contentLength).toInt()
                            _status.value = OtaStatus.Downloading(percent)
                        }
                    }
                    output.flush()
                }
            }

            _status.value = OtaStatus.ReadyToInstall(outputFile)

        } catch (e: Exception) {
            _status.value = OtaStatus.Error("Erro ao baixar APK: ${e.message}")
        }
    }

    fun launchInstaller(apkFile: File) {
        if (!apkFile.exists()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    private fun showUpdateNotification(title: String, message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val channelId = "tai_ota_updates"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Atualizações do Taí",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos de novas versões e compilações do Taí"
                    enableLights(true)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = if (launchIntent != null) {
                PendingIntent.getActivity(
                    context,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )
            } else null

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(1001, notification)
        } catch (_: Exception) {
            // Em caso de restrição de permissão em versões específicas do Android
        }
    }

    private fun isVersionNewer(remoteVersion: String, localVersion: String, localComp: Int): Boolean {
        // e.g. "0.0.2", "0.0.1_Comp2", "0.0.1.2"
        try {
            val remoteClean = remoteVersion.substringBefore("_").substringBefore("-")
            val localClean = localVersion.substringBefore("_").substringBefore("-")

            val remoteParts = remoteClean.split(".").mapNotNull { it.toIntOrNull() }
            val localParts = localClean.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }

            // Check compilation suffix if present (e.g. Comp2 or Comp. 2)
            if (remoteVersion.contains("Comp", ignoreCase = true)) {
                val remoteCompNumber = Regex("""\d+""").findAll(remoteVersion.substringAfter("Comp")).firstOrNull()?.value?.toIntOrNull() ?: 0
                if (remoteCompNumber > localComp) return true
            }

            return false
        } catch (_: Exception) {
            return remoteVersion != localVersion
        }
    }
}
