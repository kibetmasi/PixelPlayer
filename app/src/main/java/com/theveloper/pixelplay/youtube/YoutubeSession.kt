package com.theveloper.pixelplay.youtube

import android.content.Context
import android.webkit.CookieManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class YoutubeAccount(
    val cookieHeader: String = "",
    val displayName: String = "",
) {
    val isSignedIn: Boolean get() = cookieHeader.isNotBlank()
}

@Singleton
class YoutubeSession @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _account = MutableStateFlow(read())
    val account: StateFlow<YoutubeAccount> = _account.asStateFlow()

    fun cookieHeader(): String = _account.value.cookieHeader

    fun isSignedIn(): Boolean = _account.value.isSignedIn

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
        prefs.edit().clear().apply()
        _account.value = YoutubeAccount()
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies(null)
        cookies.removeSessionCookies(null)
        cookies.flush()
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
