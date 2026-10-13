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
        omni.connection.check()
        setContent {
            val settings by omni.settings.flow.collectAsStateWithLifecycle()
            OmniTheme(settings.theme) {
                Surface { AppRoot() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val omni = application as OmniApp
        val state = omni.connection.state.value
        if (state !is ConnState.Ok && state !is ConnState.Checking) {
            omni.connection.check()
        }
    }
}
