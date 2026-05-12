package com.spotitidal.platform

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class OAuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null) {
            OAuthHandler.onCallback(uri)
        }
        finish()
    }
}
