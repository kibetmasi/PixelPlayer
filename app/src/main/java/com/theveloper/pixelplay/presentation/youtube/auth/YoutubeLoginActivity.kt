package com.theveloper.pixelplay.presentation.youtube.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import com.theveloper.pixelplay.youtube.YoutubeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class YoutubeLoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelPlayTheme {
                YoutubeBrowserSignInScreen(onClose = { finish() })
            }
        }
    }

    companion object {
        const val YOUTUBE_HOME = "https://www.youtube.com/"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YoutubeBrowserSignInScreen(
    onClose: () -> Unit,
    viewModel: YoutubeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var cookies by remember { mutableStateOf("") }
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.youtube_sign_in),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.youtube_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.youtube_signin_browser_body),
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(YoutubeLoginActivity.YOUTUBE_HOME)),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.youtube_open_chrome), fontFamily = GoogleSansRounded)
            }
            OutlinedTextField(
                value = cookies,
                onValueChange = { cookies = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text(stringResource(R.string.youtube_cookie_label)) },
            )
            Button(
                onClick = {
                    val saved = viewModel.importSession(cookies)
                    if (saved) {
                        onClose()
                    } else {
                        Toast.makeText(context, R.string.youtube_cookie_invalid, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = cookies.isNotBlank(),
            ) {
                Text(stringResource(R.string.youtube_cookie_save), fontFamily = GoogleSansRounded)
            }
        }
    }
}
