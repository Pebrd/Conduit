package com.conduit

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import com.conduit.di.appModule

class ConduitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ConduitApp)
            modules(appModule, com.conduit.platform.platformModule)
        }
    }
}
