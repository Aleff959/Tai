package com.tai.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamExtractor
import java.util.Locale

object NativeYouTubeExtractor {

    private var isInitialized = false

    @Synchronized
    fun ensureInitialized() {
        if (!isInitialized) {
            val downloader = AppNewPipeDownloader.create()
            NewPipe.init(downloader, Localization(Locale.getDefault().language, Locale.getDefault().country))
            isInitialized = true
        }
    }

    suspend fun extract(url: String): MediaItem = withContext(Dispatchers.IO) {
        ensureInitialized()

        val service = NewPipe.getServiceByUrl(url) 
            ?: NewPipe.getService("YouTube")

        val streamExtractor: StreamExtractor = service.getStreamExtractor(url)
        streamExtractor.fetchPage()

        val title = streamExtractor.name ?: "Vídeo do YouTube"
        val author = streamExtractor.uploaderName ?: "YouTube"

        // 1. Tenta obter fluxo combinado (vídeo + áudio juntos)
        val combinedStreams = streamExtractor.videoStreams
        var selectedVideoUrl: String? = null

        if (!combinedStreams.isNullOrEmpty()) {
            // Escolhe a melhor resolução combinada (ex.: 720p, 360p)
            val bestCombined = combinedStreams.maxByOrNull { it.getResolution() ?: "" } ?: combinedStreams.first()
            selectedVideoUrl = bestCombined.content
        }

        // 2. Se não houver fluxo combinado direto, pega o melhor videoOnlyStream
        if (selectedVideoUrl.isNullOrEmpty()) {
            val videoOnlyStreams = streamExtractor.videoOnlyStreams
            if (!videoOnlyStreams.isNullOrEmpty()) {
                val bestVideo = videoOnlyStreams.maxByOrNull { it.getResolution() ?: "" } ?: videoOnlyStreams.first()
                selectedVideoUrl = bestVideo.content
            }
        }

        if (selectedVideoUrl.isNullOrEmpty()) {
            throw Exception("Nenhum formato de vídeo reproduzível encontrado no YouTube.")
        }

        MediaItem(
            type = MediaType.VIDEO,
            sourcePlatform = "YouTube",
            title = title,
            author = author,
            videoUrl = selectedVideoUrl
        )
    }
}
