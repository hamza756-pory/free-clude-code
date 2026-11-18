package com.hamza.claudecode.mobile

import android.app.Application
import com.hamza.claudecode.mobile.data.db.AppDatabase
import com.hamza.claudecode.mobile.data.network.OpenRouterClient
import com.hamza.claudecode.mobile.data.repository.ChatRepository
import com.hamza.claudecode.mobile.data.repository.ModelCatalog
import com.hamza.claudecode.mobile.data.security.SecureKeyStore

/**
 * Application class that owns a tiny manual dependency container.
 */
class ClaudeCodeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val keyStore: SecureKeyStore = SecureKeyStore(app)
    val openRouter: OpenRouterClient = OpenRouterClient(keyProvider = { keyStore.getApiKey() })
    private val database: AppDatabase = AppDatabase.get(app)
    val chatRepository: ChatRepository = ChatRepository(database.chatDao())
    val modelCatalog: ModelCatalog = ModelCatalog(openRouter, keyStore)
}
