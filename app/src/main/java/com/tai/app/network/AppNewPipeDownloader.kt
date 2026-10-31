package com.tai.app.network

import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class AppNewPipeDownloader private constructor(okHttpClientBuilder: OkHttpClient.Builder) : Downloader() {

    private val client: OkHttpClient = okHttpClientBuilder
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        fun create(builder: OkHttpClient.Builder = OkHttpClient.Builder()): AppNewPipeDownloader {
            return AppNewPipeDownloader(builder)
        }
    }

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val okHeaders = headers.entries.associate { it.key to it.value.joinToString(",") }

        val requestBody = if (dataToSend != null && (httpMethod == "POST" || httpMethod == "PUT" || httpMethod == "PATCH")) {
            val contentType = headers["Content-Type"]?.firstOrNull() ?: "application/json; charset=utf-8"
            dataToSend.toRequestBody(contentType.toMediaType())
        } else {
            null
        }

        val okHttpRequest = OkHttpRequest.Builder()
            .url(url)
            .headers(okHeaders.toHeaders())
            .method(httpMethod, requestBody)
            .build()

        client.newCall(okHttpRequest).execute().use { response ->
            val responseCode = response.code
            val responseMessage = response.message
            val responseHeaders = response.headers.toMultimap()
            val responseBody = response.body?.string() ?: ""
            val latestUrl = response.request.url.toString()

            return Response(
                responseCode,
                responseMessage,
                responseHeaders,
                responseBody,
                latestUrl
            )
        }
    }
}
