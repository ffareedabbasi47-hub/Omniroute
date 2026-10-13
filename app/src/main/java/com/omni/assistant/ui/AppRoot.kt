package com.omni.assistant.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

enum class AppScreen {
    CHAT,
    AGENT,
    HISTORY,
    VOICE,
    SETTINGS
}

@Composable
fun AppRoot() {
    var currentScreen by remember { mutableStateOf(AppScreen.CHAT) }
    val chatVm: ChatViewModel = viewModel()
    val agentVm: AgentViewModel = viewModel()

    BackHandler(enabled = currentScreen != AppScreen.CHAT) {
        currentScreen = AppScreen.CHAT
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            label = "ScreenTransition",
            transitionSpec = { fadeIn() togetherWith fadeOut() }
        ) { screen ->
            when (screen) {
                AppScreen.CHAT -> ChatScreen(
                    vm = chatVm,
                    onHistory = { currentScreen = AppScreen.HISTORY },
                    onSettings = { currentScreen = AppScreen.SETTINGS },
                    onAgent = { currentScreen = AppScreen.AGENT },
                    onVoice = { currentScreen = AppScreen.VOICE },
                    onModels = { currentScreen = AppScreen.SETTINGS }
                )
                AppScreen.AGENT -> AgentScreen(
                    vm = agentVm,
                    onBack = { currentScreen = AppScreen.CHAT },
                    onSettings = { currentScreen = AppScreen.SETTINGS }
                )
                AppScreen.HISTORY -> HistoryScreen(
                    vm = chatVm,
                    onBack = { currentScreen = AppScreen.CHAT },
                    onOpened = { currentScreen = AppScreen.CHAT }
                )
                AppScreen.VOICE -> VoiceScreen(
                    vm = chatVm,
                    onBack = { currentScreen = AppScreen.CHAT },
                    onModels = { currentScreen = AppScreen.SETTINGS }
                )
                AppScreen.SETTINGS -> SettingsScreen(
                    chatVm = chatVm,
                    onBack = { currentScreen = AppScreen.CHAT },
                    onChooseModel = { /* handled in settings */ }
                )
            }
        }
    }
}
