package ai.pocket.heyclaude.launch

import ai.pocket.heyclaude.Constants
import ai.pocket.heyclaude.Prefs
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

/**
 * Wakes the screen and brings the active assistant app (Claude or ChatGPT, see [Prefs.assistant])
 * to the front.
 *
 * Starting an activity from the background is allowed on Android 10+ because the app
 * hosts a bound AccessibilityService (and the install script also grants SYSTEM_ALERT_WINDOW).
 */
object AssistantLauncher {
    private const val WAKE_MS = 10_000L

    /** @return false if the assistant package is not installed / has no launch activity. */
    fun launch(ctx: Context): Boolean {
        val app = ctx.applicationContext
        val pkg = Prefs.assistantPackage(app)
        val intent = app.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Log.w(Constants.TAG, "Launcher: no launch intent for $pkg (not installed or not visible)")
            return false
        }

        wakeScreen(app)

        val wantTap = Prefs.autoTap(app)
        // Mark state before starting the activity so the a11y service sees the first window event.
        LaunchState.onLaunch(wantTap)

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            app.startActivity(intent)
            Log.i(Constants.TAG, "Launcher: started $pkg (autoTap=$wantTap, a11y=${VoiceModeA11yService.isConnected})")
            // If the assistant was already in front no window-state event may follow; poke the service.
            VoiceModeA11yService.onLaunchRequested()
            true
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Launcher: startActivity failed for $pkg", e)
            LaunchState.endSession()
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen(ctx: Context) {
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "HeyClaude:launch"
            )
            wl.setReferenceCounted(false)
            wl.acquire(WAKE_MS)
        } catch (e: Exception) {
            Log.w(Constants.TAG, "Launcher: wake lock failed", e)
        }
    }
}
