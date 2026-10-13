package com.omni.assistant.net

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object TermuxBridgeManager {

    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"

    /**
     * Attempts to start the local Antigravity bridge in Termux in the background.
     */
    fun startBridge(context: Context) {
        val script = "/data/data/com.termux/files/home/OmniAssistant/run-bridge.sh"
        try {
            // First try direct service launch
            val serviceIntent = Intent(ACTION_RUN_COMMAND).apply {
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra(EXTRA_PATH, "/data/data/com.termux/files/usr/bin/bash")
                putExtra(EXTRA_ARGUMENTS, arrayOf("-c", "nohup bash $script >/dev/null 2>&1 &"))
                putExtra(EXTRA_WORKDIR, "/data/data/com.termux/files/home/OmniAssistant")
                putExtra(EXTRA_BACKGROUND, true)
            }
            context.startService(serviceIntent)
        } catch (e: Exception) {
            try {
                // Fallback to broadcast intent
                val broadcastIntent = Intent(ACTION_RUN_COMMAND).apply {
                    setPackage("com.termux")
                    putExtra(EXTRA_PATH, "/data/data/com.termux/files/usr/bin/bash")
                    putExtra(EXTRA_ARGUMENTS, arrayOf("-c", "nohup bash $script >/dev/null 2>&1 &"))
                    putExtra(EXTRA_WORKDIR, "/data/data/com.termux/files/home/OmniAssistant")
                    putExtra(EXTRA_BACKGROUND, true)
                }
                context.sendBroadcast(broadcastIntent)
            } catch (t: Throwable) {
            }
        }
    }

    /**
     * Launches Termux app directly if available so the user can see the console.
     */
    fun openTermux(context: Context) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.termux")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        } catch (t: Throwable) {
        }
    }
}
