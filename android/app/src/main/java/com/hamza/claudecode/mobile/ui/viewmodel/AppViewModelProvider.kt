package com.hamza.claudecode.mobile.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hamza.claudecode.mobile.ClaudeCodeApp

object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = claudeApp()
            ChatViewModel(
                application = app,
                repository = app.container.chatRepository,
                client = app.container.openRouter,
                keyStore = app.container.keyStore,
                catalog = app.container.modelCatalog
            )
        }
        initializer {
            val app = claudeApp()
            KeyViewModel(
                keyStore = app.container.keyStore,
                client = app.container.openRouter,
                catalog = app.container.modelCatalog
            )
        }
    }
}

private fun CreationExtras.claudeApp(): ClaudeCodeApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ClaudeCodeApp
