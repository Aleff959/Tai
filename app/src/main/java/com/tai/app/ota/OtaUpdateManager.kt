package com.tai.app.ota

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.tai.app.core.CoreHostManager
import com.tai.app.core.TaiCoreConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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
        val assetName: String,
        val targetComp: Int = 0,
        val isHotSwap: Boolean = false
    ) : OtaStatus()
    data class Downloading(val progressPercent: Int) : OtaStatus()
    data class ReadyToInstall(val apkFile: File) : OtaStatus()
    data class ReadyToHotSwap(val targetComp: Int) : OtaStatus()
    data class Error(val message: String) : OtaStatus()
}

class OtaUpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Configurable repo: defaults to Aleff959/Tai
    var repoOwner: String = "Aleff959"
    var repoName: String = "Tai"

    private val prefs = context.getSharedPreferences("ota_prefs", Context.MODE_PRIVATE)

    var channel: String
        get() = prefs.getString("update_channel", "stable") ?: "stable"
        set(value) = prefs.edit().putString("update_channel", value).apply()

    private val _status = MutableStateFlow<OtaStatus>(OtaStatus.Idle)
    val status: StateFlow<OtaStatus> = _status.asStateFlow()

    fun resetStatus() {
        _status.value = OtaStatus.Idle
    }

    private fun getLocalAppVersion(): Pair<String, Int> {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val apkVersionName = pInfo.versionName ?: TaiCoreConfig.versionName()
            val apkComp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
            val coreComp = TaiCoreConfig.compNumber()
            val activeComp = CoreHostManager.getActiveCompilation(context, maxOf(apkComp, coreComp))
            Pair(apkVersionName, activeComp)
        } catch (_: Exception) {
            Pair(TaiCoreConfig.versionName(), TaiCoreConfig.compNumber())
        }
    }

    suspend fun checkForUpdates(
        providedVersion: String? = null,
        providedComp: Int? = null
    ) = withContext(Dispatchers.IO) {
        _status.value = OtaStatus.Checking
        try {
            val (currentVersion, currentComp) = if (providedVersion != null && providedComp != null) {
                Pair(providedVersion, providedComp)
            } else {
                getLocalAppVersion()
            }

            val branch = if (channel == "beta") "beta" else "main"
            val cdnManifestUrl = "https://raw.githubusercontent.com/$repoOwner/$repoName/$branch/ota_latest.json"

            var tagName = ""
            var releaseName = ""
            var releaseNotes = ""
            var apkUrl: String? = null
            var apkName = "Tai_update.apk"
            var remoteComp = 0
            var isHotSwap = false

            // 1. Tenta carregar via CDN raw (não possui rate limit de 60 req/h do GitHub)
            var loadedFromCdn = false
            try {
                val cdnReq = Request.Builder()
                    .url(cdnManifestUrl)
                    .header("User-Agent", "Tai-Android-App")
                    .build()
                client.newCall(cdnReq).execute().use { cdnResp ->
                    if (cdnResp.isSuccessful) {
                        val cdnBody = cdnResp.body?.string() ?: ""
                        if (cdnBody.isNotEmpty()) {
                            val cdnJson = JSONObject(cdnBody)
                            tagName = cdnJson.optString("tagName", "").trim()
                            releaseName = cdnJson.optString("releaseName", tagName)
                            releaseNotes = cdnJson.optString("notes", "Sem notas de atualização.")
                            remoteComp = cdnJson.optInt("comp", 0)
                            val dexUrl = cdnJson.optString("dexUrl", "").trim()
                            if (dexUrl.isNotEmpty()) {
                                apkUrl = dexUrl
                                apkName = "taicore_v$remoteComp.dex"
                                isHotSwap = true
                            } else {
                                apkUrl = cdnJson.optString("apkUrl", "")
                                apkName = cdnJson.optString("apkName", "Tai_update.apk")
                                isHotSwap = false
                            }
                            if (tagName.isNotEmpty() && !apkUrl.isNullOrEmpty()) {
                                loadedFromCdn = true
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Se não carregou do CDN, recorre à API REST do GitHub
            if (!loadedFromCdn) {
                val url = if (channel == "beta") {
                    "https://api.github.com/repos/$repoOwner/$repoName/releases?per_page=1"
                } else {
                    "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
                }
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Tai-Android-App")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 404) {
                            _status.value = OtaStatus.UpToDate
                        } else if (response.code == 403) {
                            _status.value = OtaStatus.Error("Limite da API do GitHub atingido temporariamente (HTTP 403).")
                        } else {
                            _status.value = OtaStatus.Error("Erro ao buscar atualizações (HTTP ${response.code})")
                        }
                        return@withContext
                    }

                    val body = response.body?.string() ?: ""
                    val json = if (channel == "beta") {
                        val array = JSONArray(body)
                        if (array.length() == 0) {
                            _status.value = OtaStatus.UpToDate
                            return@withContext
                        }
                        array.getJSONObject(0)
                    } else {
                        JSONObject(body)
                    }
                    tagName = json.optString("tag_name", "").trim()
                    releaseName = json.optString("name", tagName)
                    releaseNotes = json.optString("body", "Sem notas de atualização.")
                    val assets = json.optJSONArray("assets")

                    if (assets != null) {
                        // Prefere módulo .dex (Two-Stage Hot-Swap) se disponível no release
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".dex", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url")
                                apkName = name
                                isHotSwap = true
                                break
                            }
                        }
                        if (apkUrl.isNullOrEmpty()) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.optString("name", "")
                                if (name.endsWith(".apk", ignoreCase = true)) {
                                    apkUrl = asset.optString("browser_download_url")
                                    apkName = name
                                    isHotSwap = false
                                    break
                                }
                            }
                        }
                    }
                }
            }

            val finalApkUrl = apkUrl
            if (finalApkUrl.isNullOrEmpty()) {
                _status.value = OtaStatus.Error("Nenhum arquivo de atualização anexado ao release.")
                return@withContext
            }

            val cleanTag = tagName.removePrefix("v").trim()
            if (remoteComp <= 0 && cleanTag.contains("Comp", ignoreCase = true)) {
                remoteComp = Regex("""\d+""").findAll(cleanTag.substringAfter("Comp")).firstOrNull()?.value?.toIntOrNull() ?: 0
            }

            val hasNewVersion = if (remoteComp > 0) {
                remoteComp > currentComp
            } else {
                isVersionNewer(cleanTag, currentVersion, currentComp)
            }

            if (hasNewVersion) {
                _status.value = OtaStatus.UpdateAvailable(
                    version = cleanTag,
                    title = releaseName,
                    changelog = releaseNotes,
                    downloadUrl = finalApkUrl,
                    assetName = apkName,
                    targetComp = remoteComp,
                    isHotSwap = isHotSwap
                )

                // Previne repetição contínua de notificação para a mesma versão
                val prefs = context.getSharedPreferences("ota_prefs", Context.MODE_PRIVATE)
                val lastNotified = prefs.getString("last_notified_release", "")
                if (lastNotified != cleanTag) {
                    showUpdateNotification(releaseName, "Nova versão disponível: $releaseName. Toque para atualizar.")
                    prefs.edit().putString("last_notified_release", cleanTag).apply()
                }
            } else {
                _status.value = OtaStatus.UpToDate
            }

        } catch (e: Exception) {
            _status.value = OtaStatus.Error(e.message ?: "Falha ao verificar atualizações")
        }
    }

    suspend fun downloadAndPrepareInstall(
        downloadUrl: String,
        fileName: String,
        targetComp: Int = 0,
        isHotSwap: Boolean = false
    ) = withContext(Dispatchers.IO) {
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

            val isDexModule = isHotSwap || fileName.endsWith(".dex", ignoreCase = true)
            val resolvedComp = if (targetComp > 0) targetComp else TaiCoreConfig.compNumber() + 1

            val outputFile = if (isDexModule) {
                CoreHostManager.getPendingTempFile(context, resolvedComp)
            } else {
                val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                File(updateDir, fileName)
            }

            if (outputFile.exists()) {
                outputFile.setWritable(true)
                outputFile.delete()
            }

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

            if (isDexModule) {
                val staged = CoreHostManager.stagePendingUpdate(context, resolvedComp, outputFile)
                if (staged) {
                    _status.value = OtaStatus.ReadyToHotSwap(resolvedComp)
                } else {
                    _status.value = OtaStatus.Error("BootGuard: O módulo baixado falhou na verificação de integridade.")
                }
            } else {
                _status.value = OtaStatus.ReadyToInstall(outputFile)
            }

        } catch (e: Exception) {
            _status.value = OtaStatus.Error("Erro ao baixar atualização: ${e.message}")
        }
    }

    fun launchInstaller(apkFile: File) {
        if (!apkFile.exists()) {
            _status.value = OtaStatus.Error("Arquivo de instalação não encontrado.")
            return
        }

        try {
            // Em Android 8.0+ (API 26+), verifica se tem permissão para instalar fontes desconhecidas
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

            // Concede permissão explícita para os gerenciadores de pacote que vão processar o URI
            val resolveInfos = context.packageManager.queryIntentActivities(installIntent, 0)
            for (info in resolveInfos) {
                context.grantUriPermission(
                    info.activityInfo.packageName,
                    apkUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            _status.value = OtaStatus.Error("Falha ao abrir instalador: ${e.message}")
        }
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
