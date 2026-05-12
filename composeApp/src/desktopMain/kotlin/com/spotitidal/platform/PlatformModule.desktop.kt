package com.spotitidal.platform

import com.russhwolf.multiplatform.settings.PreferencesSettings
import com.russhwolf.multiplatform.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.prefs.Preferences

actual val platformModule: Module = module {
    single<Settings> {
        val preferences = Preferences.userRoot().node("com.spotitidal.settings")
        PreferencesSettings(preferences)
    }

    single { OAuthHandler(get()) }
}
