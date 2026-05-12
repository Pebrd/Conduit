package com.spotitidal

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import com.spotitidal.di.appModule

class SpotiTidalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SpotiTidalApp)
            modules(appModule, com.spotitidal.platform.platformModule)
        }
    }
}
