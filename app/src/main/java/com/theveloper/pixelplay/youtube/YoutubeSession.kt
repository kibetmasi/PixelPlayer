package com.theveloper.pixelplay.youtube

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class YoutubeAccount(
    val cookieHeader: String = "",
    val displayName: String = "",
) {
    val isSignedIn: Boolean
        get() = listOf("SAPISID", "__Secure-1PAPISID", "__Secure-3PAPISID")
            .any { key -> cookieHeader.contains("$key=", ignoreCase = true) }
}

@Singleton
class YoutubeSession @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _account = MutableStateFlow(read())
    val account: StateFlow<YoutubeAccount> = _account.asStateFlow()

    private val generation = AtomicInteger(0)

    fun cookieHeader(): String = _account.value.cookieHeader

    fun isSignedIn(): Boolean = _account.value.isSignedIn

    fun generation(): Int = generation.get()

    fun save(cookieHeader: String, displayName: String = "") {
        val account = YoutubeAccount(
            cookieHeader = cookieHeader.trim(),
            displayName = displayName.trim().ifBlank { "Signed in" },
        )
        prefs.edit()
            .putString(KEY_COOKIES, account.cookieHeader)
            .putString(KEY_NAME, account.displayName)
            .apply()
        _account.value = account
    }

    fun clear() {
        generation.incrementAndGet()
        prefs.edit().clear().commit()
        _account.value = YoutubeAccount()
        val wipe = Runnable {
            val cookies = CookieManager.getInstance()
            expireStoredCookies(cookies)
            cookies.removeAllCookies { cookies.flush() }
            cookies.removeSessionCookies(null)
            cookies.flush()
            runCatching { WebStorage.getInstance().deleteAllData() }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) wipe.run()
        else Handler(Looper.getMainLooper()).post(wipe)
    }

    private fun expireStoredCookies(cookies: CookieManager) {
        val expired = "Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:01 GMT; Path=/; Secure"
        listOf(
            "https://youtube.com",
            "https://www.youtube.com",
            "https://m.youtube.com",
            "https://music.youtube.com",
            "https://accounts.google.com",
            "https://www.google.com",
            "https://google.com",
            "https://consent.youtube.com",
        ).forEach { url ->
            cookies.getCookie(url).orEmpty().split(';').forEach { part ->
                val name = part.substringBefore('=').trim()
                if (name.isNotEmpty()) cookies.setCookie(url, "$name=; $expired")
            }
        }
    }

    private fun read(): YoutubeAccount = YoutubeAccount(
        cookieHeader = prefs.getString(KEY_COOKIES, "").orEmpty(),
        displayName = prefs.getString(KEY_NAME, "").orEmpty(),
    )

    companion object {
        private const val PREFS = "youtube_session"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_NAME = "display_name"
    }
}
