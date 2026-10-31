package com.tai.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class MediaExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun extract(rawUrl: String): MediaItem = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(rawUrl)

        when {
            cleanUrl.contains("tiktok.com", ignoreCase = true) -> extractTikTok(cleanUrl)
            cleanUrl.contains("twitter.com", ignoreCase = true) || cleanUrl.contains("x.com", ignoreCase = true) -> extractTwitter(cleanUrl)
            cleanUrl.contains("instagram.com", ignoreCase = true) -> extractInstagram(cleanUrl)
            else -> {
                // Tenta extrair via motor genérico Cobalt
                extractGeneric(cleanUrl)
            }
        }
    }

    private fun sanitizeUrl(input: String): String {
        val matcher = Pattern.compile("https?://[^\\s]+").matcher(input)
        return if (matcher.find()) {
            matcher.group()
        } else {
            input.trim()
        }
    }

    private fun fixUrl(url: String, baseUrl: String = "https://www.tikwm.com"): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "$baseUrl$trimmed"
            !trimmed.startsWith("http://") && !trimmed.startsWith("https://") -> "https://$trimmed"
            else -> trimmed
        }
    }

    private fun extractTikTok(url: String): MediaItem {
        val formBody = FormBody.Builder()
            .add("url", url)
            .add("count", "12")
            .add("cursor", "0")
            .add("web", "1")
            .add("hd", "1")
            .build()

        val request = Request.Builder()
            .url("https://www.tikwm.com/api/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .post(formBody)
            .build()

        val response = client.newCall(request).execute()
        val jsonStr = response.body?.string() ?: throw Exception("Resposta vazia do TikTok")
        val json = JSONObject(jsonStr)

        if (json.optInt("code", -1) != 0) {
            val msg = json.optString("msg", "Falha ao resolver mídia do TikTok")
            throw Exception(msg)
        }

        val data = json.getJSONObject("data")
        val title = data.optString("title", "TikTok Post")
        val author = data.optJSONObject("author")?.optString("unique_id", "TikTok User")
        val rawMusic = data.optString("music", "")
        val musicUrl = if (rawMusic.isNotEmpty()) fixUrl(rawMusic) else ""
        val musicDuration = data.optJSONObject("music_info")?.optLong("duration", 0L) ?: (data.optLong("duration", 0L))

        // Verifica imagens (modo carrossel ou foto)
        val imagesJson = data.optJSONArray("images")
        if (imagesJson != null && imagesJson.length() > 0) {
            val imgList = mutableListOf<String>()
            for (i in 0 until imagesJson.length()) {
                val rawImg = imagesJson.getString(i)
                imgList.add(fixUrl(rawImg))
            }

            return if (imgList.size == 1 && musicUrl.isNotEmpty()) {
                MediaItem(
                    type = MediaType.SINGLE_PHOTO_WITH_AUDIO,
                    sourcePlatform = "TikTok",
                    title = title,
                    author = author,
                    imageUrls = imgList,
                    audioUrl = musicUrl,
                    audioDurationMs = musicDuration * 1000L
                )
            } else if (imgList.size == 1) {
                MediaItem(
                    type = MediaType.SINGLE_PHOTO,
                    sourcePlatform = "TikTok",
                    title = title,
                    author = author,
                    imageUrls = imgList
                )
            } else {
                MediaItem(
                    type = MediaType.CAROUSEL_PHOTOS,
                    sourcePlatform = "TikTok",
                    title = title,
                    author = author,
                    imageUrls = imgList,
                    audioUrl = if (musicUrl.isNotEmpty()) musicUrl else null,
                    audioDurationMs = musicDuration * 1000L
                )
            }
        }

        // Caso seja vídeo
        var playUrl = data.optString("play", "")
        if (playUrl.isEmpty()) {
            playUrl = data.optString("wmplay", "")
        }

        playUrl = fixUrl(playUrl)

        if (playUrl.isEmpty()) {
            throw Exception("Nenhum vídeo encontrado no post")
        }

        return MediaItem(
            type = MediaType.VIDEO,
            sourcePlatform = "TikTok",
            title = title,
            author = author,
            videoUrl = playUrl
        )
    }

    private fun extractTwitter(url: String): MediaItem {
        // Tenta resolver via FxTwitter API
        val tweetId = extractTwitterStatusId(url)
        if (tweetId.isNotEmpty()) {
            try {
                val apiUrl = "https://api.fxtwitter.com/status/$tweetId"
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", "TaiApp/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val tweet = json.optJSONObject("tweet")
                    if (tweet != null) {
                        val media = tweet.optJSONObject("media")
                        val title = tweet.optString("text", "Post do X/Twitter")
                        val author = tweet.optJSONObject("author")?.optString("screen_name", "Twitter")

                        val videos = media?.optJSONArray("videos")
                        if (videos != null && videos.length() > 0) {
                            val vidObj = videos.getJSONObject(0)
                            val videoUrl = vidObj.optString("url")
                            return MediaItem(
                                type = MediaType.VIDEO,
                                sourcePlatform = "X/Twitter",
                                title = title,
                                author = author,
                                videoUrl = videoUrl
                            )
                        }

                        val photos = media?.optJSONArray("photos")
                        if (photos != null && photos.length() > 0) {
                            val photoList = mutableListOf<String>()
                            for (i in 0 until photos.length()) {
                                photoList.add(photos.getJSONObject(i).getString("url"))
                            }
                            return if (photoList.size == 1) {
                                MediaItem(
                                    type = MediaType.SINGLE_PHOTO,
                                    sourcePlatform = "X/Twitter",
                                    title = title,
                                    author = author,
                                    imageUrls = photoList
                                )
                            } else {
                                MediaItem(
                                    type = MediaType.CAROUSEL_PHOTOS,
                                    sourcePlatform = "X/Twitter",
                                    title = title,
                                    author = author,
                                    imageUrls = photoList
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback para cobalt se falhar
            }
        }

        return extractGeneric(url, defaultPlatform = "X/Twitter")
    }

    private fun extractInstagram(url: String): MediaItem {
        // Primeiro tenta via instância pública rápida de extração
        try {
            return extractGeneric(url, defaultPlatform = "Instagram Reels")
        } catch (_: Exception) {
            // Tentativa secundária
            return extractInstagramFallback(url)
        }
    }

    private fun extractInstagramFallback(url: String): MediaItem {
        // Constrói requisição para serviço de resolução de reels
        val jsonPayload = JSONObject().apply {
            put("url", url)
        }
        val request = Request.Builder()
            .url("https://api.cobalt.tools/")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "TaiApp/1.0")
            .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Erro ao processar Instagram")
        val json = JSONObject(body)

        val streamUrl = json.optString("url", "")
        if (streamUrl.isNotEmpty()) {
            return MediaItem(
                type = MediaType.VIDEO,
                sourcePlatform = "Instagram",
                title = "Instagram Reel",
                videoUrl = streamUrl
            )
        }

        val picker = json.optJSONArray("picker")
        if (picker != null && picker.length() > 0) {
            val photos = mutableListOf<String>()
            var videoFound: String? = null
            for (i in 0 until picker.length()) {
                val item = picker.getJSONObject(i)
                val type = item.optString("type", "")
                val itemUrl = item.optString("url", "")
                if (type == "video") {
                    videoFound = itemUrl
                    break
                } else if (type == "photo") {
                    photos.add(itemUrl)
                }
            }
            if (videoFound != null) {
                return MediaItem(
                    type = MediaType.VIDEO,
                    sourcePlatform = "Instagram",
                    title = "Instagram Reel",
                    videoUrl = videoFound
                )
            }
            if (photos.isNotEmpty()) {
                return if (photos.size == 1) {
                    MediaItem(
                        type = MediaType.SINGLE_PHOTO,
                        sourcePlatform = "Instagram",
                        imageUrls = photos
                    )
                } else {
                    MediaItem(
                        type = MediaType.CAROUSEL_PHOTOS,
                        sourcePlatform = "Instagram",
                        imageUrls = photos
                    )
                }
            }
        }

        throw Exception("Não foi possível extrair a mídia do Instagram")
    }

    private fun extractGeneric(url: String, defaultPlatform: String = "Rede Social"): MediaItem {
        val instances = listOf(
            "https://api.cobalt.tools/",
            "https://co.wuk.sh/api/json",
            "https://cobalt.api.koyeb.app/"
        )

        var lastException: Exception? = null

        for (endpoint in instances) {
            try {
                val jsonPayload = JSONObject().apply {
                    put("url", url)
                    put("videoQuality", "max")
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                val json = JSONObject(body)

                val status = json.optString("status", "")
                if (status == "error") {
                    val text = json.optJSONObject("error")?.optString("code") ?: "Falha na extração"
                    lastException = Exception(text)
                    continue
                }

                val directUrl = json.optString("url", "")
                if (directUrl.isNotEmpty()) {
                    return MediaItem(
                        type = MediaType.VIDEO,
                        sourcePlatform = defaultPlatform,
                        title = "Vídeo baixado",
                        videoUrl = directUrl
                    )
                }

                val picker = json.optJSONArray("picker")
                if (picker != null && picker.length() > 0) {
                    val photos = mutableListOf<String>()
                    var videoUrl: String? = null
                    for (i in 0 until picker.length()) {
                        val item = picker.getJSONObject(i)
                        val itemUrl = item.optString("url", "")
                        val itemType = item.optString("type", "")
                        if (itemType == "video") {
                            videoUrl = itemUrl
                            break
                        } else {
                            photos.add(itemUrl)
                        }
                    }

                    if (videoUrl != null) {
                        return MediaItem(
                            type = MediaType.VIDEO,
                            sourcePlatform = defaultPlatform,
                            videoUrl = videoUrl
                        )
                    }

                    if (photos.isNotEmpty()) {
                        val audioUrl = json.optString("audio", "")
                        return if (photos.size == 1 && audioUrl.isNotEmpty()) {
                            MediaItem(
                                type = MediaType.SINGLE_PHOTO_WITH_AUDIO,
                                sourcePlatform = defaultPlatform,
                                imageUrls = photos,
                                audioUrl = audioUrl,
                                audioDurationMs = 15000L
                            )
                        } else if (photos.size == 1) {
                            MediaItem(
                                type = MediaType.SINGLE_PHOTO,
                                sourcePlatform = defaultPlatform,
                                imageUrls = photos
                            )
                        } else {
                            MediaItem(
                                type = MediaType.CAROUSEL_PHOTOS,
                                sourcePlatform = defaultPlatform,
                                imageUrls = photos
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                lastException = e
            }
        }

        throw lastException ?: Exception("Não foi possível processar o link")
    }

    private fun extractTwitterStatusId(url: String): String {
        val pattern = Pattern.compile("status(?:es)?/(\\d+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) {
            matcher.group(1) ?: ""
        } else {
            ""
        }
    }
}
