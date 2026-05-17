package com.conduit

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.android.ext.android.get
import com.conduit.di.appModule
import com.conduit.data.local.SettingsStorage
import com.conduit.data.local.TokenStorage

class ConduitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ConduitApp)
            modules(appModule, com.conduit.platform.platformModule)
        }

        val settingsStorage: SettingsStorage = get()
        val tokenStorage: TokenStorage = get()
        val SCOPES_VERSION = 3
        if (settingsStorage.getScopesVersion() < SCOPES_VERSION) {
            tokenStorage.clearTokens("spotify")
            tokenStorage.clearTokens("tidal")
            settingsStorage.saveScopesVersion(SCOPES_VERSION)
        }
    }
}
