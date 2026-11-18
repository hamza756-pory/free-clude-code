package com.hamza.claudecode.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamza.claudecode.mobile.BuildConfig
import com.hamza.claudecode.mobile.ui.components.ApiKeyInput
import com.hamza.claudecode.mobile.ui.components.DottedDivider
import com.hamza.claudecode.mobile.ui.components.KeyStatus
import com.hamza.claudecode.mobile.ui.components.PixelScene
import com.hamza.claudecode.mobile.ui.components.ThinkingSpinner
import com.hamza.claudecode.mobile.ui.theme.Term
import com.hamza.claudecode.mobile.ui.viewmodel.KeyViewModel
import kotlinx.coroutines.delay

private data class LoginMethod(val title: String, val subtitle: String)

private val LOGIN_METHODS = listOf(
    LoginMethod("OpenRouter API key", "paste manually"),
    LoginMethod("Paste from clipboard", "quick paste & verify"),
    LoginMethod("Get a free key", "openrouter.ai/keys")
)

/**
 * Welcome / onboarding screen replicating the Claude Code terminal welcome.
 * When a key already exists it acts as a short splash and continues to the chat.
 */
@Composable
fun WelcomeScreen(
    keyViewModel: KeyViewModel,
    onAuthenticated: () -> Unit
) {
    val apiKey by keyViewModel.apiKey.collectAsStateWithLifecycle()
    val state by keyViewModel.state.collectAsStateWithLifecycle()
    // Decide splash vs login once, based on the key present at first composition.
    val startedWithKey = rememberSaveable { !apiKey.isNullOrBlank() }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var showInput by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(startedWithKey) {
        if (startedWithKey) {
            delay(1200)
            onAuthenticated()
        }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            delay(700)
            keyViewModel.clearMessages()
            onAuthenticated()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Term.Coral)) { append("Welcome to Claude Code (Mobile) ") }
                withStyle(SpanStyle(color = Term.TextDim)) { append("v${BuildConfig.VERSION_NAME}") }
            },
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(10.dp))
        DottedDivider()
        Spacer(Modifier.height(10.dp))
        PixelScene()
        Spacer(Modifier.height(18.dp))

        if (startedWithKey) {
            ThinkingSpinner(label = "Loading your session…", showHint = false)
            Text("  ⎿ API key found (encrypted on device)", color = Term.TextDim, style = MaterialTheme.typography.bodySmall)
            return@Column
        }

        Text(
            text = "Claude Code (Mobile) runs on free AI models through your OpenRouter account. " +
                "Your API key is stored encrypted on this device only.",
            style = MaterialTheme.typography.bodyMedium,
            color = Term.Text
        )
        Spacer(Modifier.height(16.dp))
        Text("Select login method:", style = MaterialTheme.typography.bodyMedium, color = Term.Text)
        Spacer(Modifier.height(10.dp))

        LOGIN_METHODS.forEachIndexed { index, method ->
            val isSelected = index == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        selected = index
                        when (index) {
                            0 -> showInput = true
                            1 -> {
                                val text = clipboard.getText()?.text.orEmpty()
                                keyViewModel.submit(text)
                            }
                            2 -> uriHandler.openUri(KeyViewModel.KEYS_URL)
                        }
                    }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Term.Blue)) { append(if (isSelected) "❯ " else "  ") }
                        withStyle(SpanStyle(color = Term.TextDim)) { append("${index + 1}. ") }
                        withStyle(SpanStyle(color = if (isSelected) Term.Blue else Term.Text)) { append(method.title) }
                        withStyle(SpanStyle(color = Term.TextDim)) { append(" · ${method.subtitle}") }
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        AnimatedVisibility(visible = showInput) {
            Column(Modifier.padding(top = 14.dp)) {
                ApiKeyInput(
                    enabled = !state.validating,
                    onSubmit = { keyViewModel.submit(it) }
                )
            }
        }

        KeyStatus(
            validating = state.validating,
            error = state.error,
            warning = state.warning,
            info = state.info,
            onSaveAnyway = if (state.unverifiedKey != null) ({ keyViewModel.saveUnverified() }) else null
        )

        Spacer(Modifier.height(28.dp))
        DottedDivider()
        Text(
            text = "Free models · no key is bundled in the app · Designed by Hamza Alfarai",
            style = MaterialTheme.typography.labelSmall,
            color = Term.TextFaint,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
