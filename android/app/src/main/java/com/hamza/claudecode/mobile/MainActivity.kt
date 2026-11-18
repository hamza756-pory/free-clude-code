package com.hamza.claudecode.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.hamza.claudecode.mobile.ui.AppNavHost
import com.hamza.claudecode.mobile.ui.theme.ClaudeCodeTheme
import com.hamza.claudecode.mobile.ui.theme.Term

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Term.Background.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Term.Background.toArgb())
        )
        super.onCreate(savedInstanceState)
        setContent {
            ClaudeCodeTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Term.Background)
                ) {
                    AppNavHost()
                }
            }
        }
    }
}
