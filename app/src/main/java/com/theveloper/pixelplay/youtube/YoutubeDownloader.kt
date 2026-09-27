package com.theveloper.pixelplay.youtube

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * NewPipe HTTP layer with a normal browser user agent.
 * The app-wide OkHttp client sends a PixelPlayer user agent that YouTube rejects.
 */
internal class YoutubeDownloader : Downloader() {
    @Volatile
    var cookieHeader: String? = null
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun execute(request: Request): Response {
        val bodyBytes = request.dataToSend()
        val requestBody = bodyBytes?.toRequestBody(null)
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), requestBody)
            .url(request.url())
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")

        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { value -> builder.addHeader(name, value) }
        }
        cookieHeader?.trim()?.takeIf { it.isNotEmpty() }?.let { cookies ->
            builder.header("Cookie", cookies)
        }

        client.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body.string()
            if (response.code == 429) {
                throw java.io.IOException("YouTube rate limited this request. Wait a moment and try again.")
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBody,
                response.request.url.toString(),
            )
        }
    }

    fun postJson(url: String, json: String, origin: String = "https://www.youtube.com"): String {
        val cookies = cookieHeader?.trim().orEmpty()
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON))
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Origin", origin)
            .header("Referer", "$origin/")
            .header("X-Origin", origin)
            .apply {
                if (cookies.isNotEmpty()) {
                    header("Cookie", cookies)
                    authorization(cookies, origin)?.let { header("Authorization", it) }
                }
            }
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                throw java.io.IOException("YouTube browse failed (${response.code})")
            }
            return responseBody
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

        private fun authorization(cookies: String, origin: String): String? {
            val values = cookies.split(';')
                .map { it.trim() }
                .filter { it.contains('=') }
                .associate { part ->
                    val index = part.indexOf('=')
                    part.substring(0, index) to part.substring(index + 1)
                }
            val sapisid = values["SAPISID"].orEmpty().ifBlank { values["__Secure-3PAPISID"].orEmpty() }
            if (sapisid.isBlank()) return null
            val timestamp = System.currentTimeMillis() / 1000
            val digest = MessageDigest.getInstance("SHA-1")
                .digest("$timestamp $sapisid $origin".toByteArray())
            val hash = digest.joinToString("") { byte -> "%02x".format(byte) }
            return "SAPISIDHASH ${timestamp}_$hash"
        }
    }
}
