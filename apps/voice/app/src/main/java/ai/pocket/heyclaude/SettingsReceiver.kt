package ai.pocket.heyclaude

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Persists `SET_ASSISTANT` / `SET_LANG` broadcasts while [WakeWordService] is not running, so
 * the install script may send them before it starts the service. A running service handles
 * them itself in its own receiver (and also reloads its models), so this one stays out of it.
 */
class SettingsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (WakeWordService.running) return
        WakeWordService.saveSetting(context, intent)
    }
}
