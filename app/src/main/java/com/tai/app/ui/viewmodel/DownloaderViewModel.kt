package com.tai.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tai.app.network.MediaExtractor
import com.tai.app.network.MediaItem
import com.tai.app.network.MediaType
import com.tai.app.ota.OtaStatus
import com.tai.app.ota.OtaUpdateManager
import com.tai.app.storage.MediaStorageManager
import com.tai.app.storage.SavedMediaResult
import com.tai.app.storage.VideoSynthesizer
import com.tai.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class DownloadState {
    object Idle : DownloadState()
    data class Loading(val message: String, val progress: Int = -1) : DownloadState()
    data class Success(
        val message: String,
        val uri: Uri? = null,
        val file: File? = null,
        val mimeType: String = "*/*",
        val fileName: String = "",
        val savedFilesCount: Int = 1
    ) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val extractor = MediaExtractor(application)
    private val storageManager = MediaStorageManager(application)
    private val synthesizer = VideoSynthesizer(application)
    val otaManager = OtaUpdateManager(application)

    private val httpClient = OkHttpClient()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _pendingPhotoWithAudio = MutableStateFlow<MediaItem?>(null)
    val pendingPhotoWithAudio: StateFlow<MediaItem?> = _pendingPhotoWithAudio.asStateFlow()

    private val _updateChannel = MutableStateFlow(otaManager.channel)
    val updateChannel: StateFlow<String> = _updateChannel.asStateFlow()

    fun toggleUpdateChannel(): String {
        val newChannel = if (otaManager.channel == "stable") "beta" else "stable"
        otaManager.channel = newChannel
        _updateChannel.value = newChannel
        AppLogger.action("Settings", "Canal de atualização alterado para: $newChannel")
        viewModelScope.launch {
            otaManager.checkForUpdates()
        }
        return newChannel
    }

    init {
        AppLogger.i("ViewModel", "DownloaderViewModel inicializado")
        viewModelScope.launch {
            otaManager.checkForUpdates()
            extractor.checkDynamicUpdates()
        }
    }

    fun onUrlChanged(newUrl: String) {
        _urlInput.value = newUrl
    }

    fun clearInput() {
        AppLogger.action("UI", "Limpou campo de URL")
        _urlInput.value = ""
        _downloadState.value = DownloadState.Idle
    }

    fun startDownload(rawUrl: String = _urlInput.value) {
        val target = rawUrl.trim()
        if (target.isEmpty()) {
            AppLogger.e("Download", "Tentativa de download com campo vazio")
            _downloadState.value = DownloadState.Error("Cole um link válido para baixar")
            return
        }

        AppLogger.action("Download", "Iniciando processo para: $target")

        viewModelScope.launch {
            _downloadState.value = DownloadState.Loading("Identificando mídia...")
            try {
                val mediaItem = extractor.extract(target)
                AppLogger.i("Extractor", "Mídia identificada: Tipo=${mediaItem.type}, Plataforma=${mediaItem.sourcePlatform}")

                when (mediaItem.type) {
                    MediaType.VIDEO -> {
                        downloadVideo(mediaItem)
                    }
                    MediaType.SINGLE_PHOTO -> {
                        downloadSinglePhoto(mediaItem)
                    }
                    MediaType.CAROUSEL_PHOTOS -> {
                        downloadCarousel(mediaItem)
                    }
                    MediaType.SINGLE_PHOTO_WITH_AUDIO -> {
                        AppLogger.action("UI", "Post de foto com áudio detectado. Exibindo diálogo de escolha.")
                        _pendingPhotoWithAudio.value = mediaItem
                        _downloadState.value = DownloadState.Idle
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Download", "Falha na extração ou download", e)
                _downloadState.value = DownloadState.Error(e.message ?: "Falha ao processar link")
            }
        }
    }

    fun handlePhotoChoice(saveAsVideo: Boolean) {
        val item = _pendingPhotoWithAudio.value ?: return
        _pendingPhotoWithAudio.value = null

        AppLogger.action("UI", "Escolha do usuário: saveAsVideo=$saveAsVideo")

        viewModelScope.launch {
            try {
                if (saveAsVideo) {
                    synthesizeAndSaveVideo(item)
                } else {
                    downloadSinglePhoto(item)
                }
            } catch (e: Exception) {
                AppLogger.e("PhotoChoice", "Erro ao executar escolha de foto", e)
                _downloadState.value = DownloadState.Error(e.message ?: "Falha ao processar mídia")
            }
        }
    }

    fun dismissPhotoChoice() {
        AppLogger.action("UI", "Diálogo de foto com áudio cancelado")
        _pendingPhotoWithAudio.value = null
        _downloadState.value = DownloadState.Idle
    }

    private fun safeUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "https://www.tikwm.com$trimmed"
            !trimmed.startsWith("http://") && !trimmed.startsWith("https://") -> "https://$trimmed"
            else -> trimmed
        }
    }

    private fun safePlatformName(platform: String): String {
        return platform.replace(Regex("""[\\/:*?"<>|\s]+"""), "_")
    }

    private suspend fun downloadVideo(item: MediaItem) = withContext(Dispatchers.IO) {
        val rawVideoUrl = item.videoUrl ?: throw Exception("URL do vídeo não encontrada")
        val videoUrl = safeUrl(rawVideoUrl)
        AppLogger.i("Download", "Baixando vídeo de: $videoUrl")
        _downloadState.value = DownloadState.Loading("Baixando vídeo...", 0)

        val request = Request.Builder().url(videoUrl).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Erro no download: HTTP ${response.code}")

        val body = response.body ?: throw Exception("Corpo da resposta vazio")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Tai_${safePlatformName(item.sourcePlatform)}_$timestamp.mp4"

        val saved = storageManager.saveMediaStream(
            inputStream = body.byteStream(),
            fileName = fileName,
            mimeType = "video/mp4",
            contentLength = body.contentLength()
        ) { progress ->
            _downloadState.value = DownloadState.Loading("Baixando vídeo...", progress)
        }

        AppLogger.i("Storage", "Vídeo salvo com sucesso: $fileName -> ${saved.file.absolutePath}")
        _downloadState.value = DownloadState.Success(
            message = "Vídeo salvo em Download/Tai/${saved.file.name}",
            uri = saved.uri,
            file = saved.file,
            mimeType = "video/mp4",
            fileName = saved.file.name
        )
    }

    private suspend fun downloadSinglePhoto(item: MediaItem) = withContext(Dispatchers.IO) {
        val rawPhotoUrl = item.imageUrls.firstOrNull() ?: throw Exception("URL da imagem não encontrada")
        val photoUrl = safeUrl(rawPhotoUrl)
        AppLogger.i("Download", "Baixando foto de: $photoUrl")
        _downloadState.value = DownloadState.Loading("Baixando foto...", 0)

        val request = Request.Builder().url(photoUrl).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Erro ao baixar foto: HTTP ${response.code}")

        val body = response.body ?: throw Exception("Corpo da resposta vazio")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Tai_${safePlatformName(item.sourcePlatform)}_$timestamp.jpg"

        val saved = storageManager.saveMediaStream(
            inputStream = body.byteStream(),
            fileName = fileName,
            mimeType = "image/jpeg",
            contentLength = body.contentLength()
        ) { progress ->
            _downloadState.value = DownloadState.Loading("Baixando foto...", progress)
        }

        AppLogger.i("Storage", "Foto salva com sucesso: $fileName -> ${saved.file.absolutePath}")
        _downloadState.value = DownloadState.Success(
            message = "Foto salva em Download/Tai/${saved.file.name}",
            uri = saved.uri,
            file = saved.file,
            mimeType = "image/jpeg",
            fileName = saved.file.name
        )
    }

    private suspend fun downloadCarousel(item: MediaItem) = withContext(Dispatchers.IO) {
        val total = item.imageUrls.size
        AppLogger.i("Download", "Iniciando carrossel de $total fotos")
        _downloadState.value = DownloadState.Loading("Baixando fotos (0/$total)...", 0)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        var savedCount = 0
        var firstSaved: SavedMediaResult? = null
        val platformSafe = safePlatformName(item.sourcePlatform)

        item.imageUrls.forEachIndexed { index, rawImgUrl ->
            try {
                val imgUrl = safeUrl(rawImgUrl)
                val request = Request.Builder().url(imgUrl).build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body
                    if (body != null) {
                        val fileName = "Tai_${platformSafe}_${timestamp}_${index + 1}.jpg"
                        val result = storageManager.saveMediaStream(
                            inputStream = body.byteStream(),
                            fileName = fileName,
                            mimeType = "image/jpeg",
                            contentLength = body.contentLength()
                        )
                        if (firstSaved == null) firstSaved = result
                        savedCount++
                        val progress = ((savedCount.toFloat() / total) * 100).toInt()
                        _downloadState.value = DownloadState.Loading("Baixando fotos ($savedCount/$total)...", progress)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Carousel", "Erro ao baixar foto ${index + 1}", e)
            }
        }

        if (savedCount > 0) {
            AppLogger.i("Storage", "Carrossel salvo: $savedCount de $total fotos")
            _downloadState.value = DownloadState.Success(
                message = "$savedCount fotos salvas em Download/Tai/",
                uri = firstSaved?.uri,
                file = firstSaved?.file,
                mimeType = "image/jpeg",
                fileName = firstSaved?.file?.name ?: "Carrossel",
                savedFilesCount = savedCount
            )
        } else {
            throw Exception("Não foi possível baixar as fotos do carrossel")
        }
    }

    private suspend fun synthesizeAndSaveVideo(item: MediaItem) = withContext(Dispatchers.IO) {
        _downloadState.value = DownloadState.Loading("Preparando recursos...")

        val rawPhoto = item.imageUrls.firstOrNull() ?: throw Exception("Foto não encontrada")
        val rawAudio = item.audioUrl ?: throw Exception("Áudio não encontrado")
        val photoUrl = safeUrl(rawPhoto)
        val audioUrl = safeUrl(rawAudio)

        AppLogger.i("Synthesizer", "Sintetizando vídeo: Foto=$photoUrl, Áudio=$audioUrl, Duração=${item.audioDurationMs}ms")

        val app = getApplication<Application>()
        val tempImg = File(app.cacheDir, "temp_single.jpg")
        val tempAudio = File(app.cacheDir, "temp_audio.mp3")
        val tempVideo = File(app.cacheDir, "temp_out.mp4")

        // 1. Baixa imagem
        _downloadState.value = DownloadState.Loading("Baixando imagem...", 15)
        httpClient.newCall(Request.Builder().url(photoUrl).build()).execute().body?.byteStream()?.use { input ->
            FileOutputStream(tempImg).use { output -> input.copyTo(output) }
        }

        // 2. Baixa áudio
        _downloadState.value = DownloadState.Loading("Baixando áudio original...", 30)
        httpClient.newCall(Request.Builder().url(audioUrl).build()).execute().body?.byteStream()?.use { input ->
            FileOutputStream(tempAudio).use { output -> input.copyTo(output) }
        }

        // 3. Sintetiza vídeo combinando foto e duração do áudio com áudio AAC
        _downloadState.value = DownloadState.Loading("Gerando vídeo com áudio...", 45)
        synthesizer.createVideoFromImageAndAudio(
            imageFile = tempImg,
            audioFile = tempAudio,
            outputFile = tempVideo,
            audioDurationMs = item.audioDurationMs
        ) { progress ->
            _downloadState.value = DownloadState.Loading("Gerando vídeo com áudio...", progress)
        }

        // 4. Salva em Download/Tai/
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Tai_${safePlatformName(item.sourcePlatform)}_synced_$timestamp.mp4"

        val saved = storageManager.saveLocalFile(tempVideo, fileName, "video/mp4")

        // Limpeza dos temporários
        tempImg.delete()
        tempAudio.delete()
        tempVideo.delete()

        AppLogger.i("Storage", "Vídeo sintetizado salvo com sucesso: $fileName -> ${saved.file.absolutePath}")
        _downloadState.value = DownloadState.Success(
            message = "Vídeo com som salvo em Download/Tai/${saved.file.name}",
            uri = saved.uri,
            file = saved.file,
            mimeType = "video/mp4",
            fileName = saved.file.name
        )
    }
}
