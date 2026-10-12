package com.omni.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omni.assistant.ui.AppRoot
import com.omni.assistant.ui.OmniTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val omni = application as OmniApp
        setContent {
            val settings by omni.settings.flow.collectAsStateWithLifecycle()
            OmniTheme(settings.theme) {
                Surface { AppRoot() }
            }
        }
    }
}
