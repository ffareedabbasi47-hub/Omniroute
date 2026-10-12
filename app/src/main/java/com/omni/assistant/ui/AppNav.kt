package com.omni.assistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.omni.assistant.OmniApp

@Composable
fun AppRoot() {
    val omni = LocalContext.current.applicationContext as OmniApp
    val nav = rememberNavController()
    // Activity-scoped, so chat, history, settings and voice share one conversation state.
    val chatVm: ChatViewModel = viewModel()
    val agentVm: AgentViewModel = viewModel()
    val start = remember { if (omni.settings.current.onboarded) "chat" else "onboarding" }

    NavHost(navController = nav, startDestination = start) {
        composable("onboarding") {
            OnboardingScreen(
                onSetUp = {
                    omni.settings.update { it.copy(onboarded = true) }
                    nav.navigate("settings") { popUpTo("onboarding") { inclusive = true } }
                },
                onSkip = {
                    omni.settings.update { it.copy(onboarded = true) }
                    nav.navigate("chat") { popUpTo("onboarding") { inclusive = true } }
                }
            )
        }
        composable("chat") {
            ChatScreen(
                vm = chatVm,
                onHistory = { nav.navigate("history") },
                onSettings = { nav.navigate("settings") },
                onAgent = { nav.navigate("agent") },
                onVoice = { nav.navigate("voice") },
                onModels = { nav.navigate("models") }
            )
        }
        composable("history") {
            HistoryScreen(
                vm = chatVm,
                onBack = { nav.popBackStack() },
                onOpened = { nav.popBackStack("chat", inclusive = false) }
            )
        }
        composable("settings") {
            SettingsScreen(
                chatVm = chatVm,
                onBack = {
                    if (!nav.popBackStack()) nav.navigate("chat")
                },
                onChooseModel = { nav.navigate("models") }
            )
        }
        composable("models") { ModelScreen(onBack = { nav.popBackStack() }) }
        composable("voice") { VoiceScreen(vm = chatVm, onBack = { nav.popBackStack() }, onModels = { nav.navigate("models") }) }
        composable("agent") {
            AgentScreen(
                vm = agentVm,
                onBack = { nav.popBackStack() },
                onSettings = { nav.navigate("settings") }
            )
        }
    }
}
