package com.tai.app.network

import android.content.Context
import com.tai.app.util.AppLogger
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

interface DynamicMediaExtractorPlugin {
    suspend fun extract(url: String): MediaItem?
}

class ModularExtractorManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var dynamicPlugin: DynamicMediaExtractorPlugin? = null

    init {
        loadLocalDynamicDex()
    }

    private fun loadLocalDynamicDex() {
        try {
            val dexDir = File(context.filesDir, "dynamic_modules")
            val dexFile = File(dexDir, "extractors_core.dex")
            if (dexFile.exists() && dexFile.length() > 0) {
                val optimizedDir = File(context.codeCacheDir, "opt_dex").apply { mkdirs() }
                val classLoader = DexClassLoader(
                    dexFile.absolutePath,
                    optimizedDir.absolutePath,
                    null,
                    context.classLoader
                )
                val pluginClass = classLoader.loadClass("com.tai.dynamic.DynamicExtractorImpl")
                dynamicPlugin = pluginClass.getDeclaredConstructor().newInstance() as? DynamicMediaExtractorPlugin
                AppLogger.i("ModularManager", "Módulo dinâmico carregado com sucesso da memória local!")
            }
        } catch (e: Exception) {
            AppLogger.e("ModularManager", "Aviso ao carregar módulo dinâmico local", e)
        }
    }

    suspend fun checkAndUpdateModule(manifestUrl: String = "https://raw.githubusercontent.com/Aleff959/Tai/main/modules/manifest.json") = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(manifestUrl).build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext

            val body = resp.body?.string() ?: return@withContext
            val json = JSONObject(body)
            val remoteVersion = json.optInt("version", 0)
            val downloadUrl = json.optString("dexUrl", "")

            val prefs = context.getSharedPreferences("modular_prefs", Context.MODE_PRIVATE)
            val currentVersion = prefs.getInt("dex_version", 0)

            if (remoteVersion > currentVersion && downloadUrl.isNotEmpty()) {
                AppLogger.i("ModularManager", "Novo módulo dinâmico encontrado: v$remoteVersion (atual: v$currentVersion)")
                val dexDir = File(context.filesDir, "dynamic_modules").apply { mkdirs() }
                val targetFile = File(dexDir, "extractors_core.dex")
                val tempFile = File(dexDir, "extractors_temp.dex")

                val dReq = Request.Builder().url(downloadUrl).build()
                client.newCall(dReq).execute().body?.byteStream()?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                    prefs.edit().putInt("dex_version", remoteVersion).apply()
                    loadLocalDynamicDex()
                    AppLogger.i("ModularManager", "Módulo dinâmico atualizado silenciosamente para v$remoteVersion!")
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun tryExtract(url: String): MediaItem? {
        return try {
            dynamicPlugin?.extract(url)
        } catch (_: Exception) {
            null
        }
    }
}
