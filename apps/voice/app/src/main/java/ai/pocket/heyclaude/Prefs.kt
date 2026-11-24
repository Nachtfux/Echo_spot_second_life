package ai.pocket.heyclaude

import ai.pocket.heyclaude.command.CommandParser
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.Locale

object Prefs {
    private const val KEY_THRESHOLD = "threshold"
    private const val KEY_ASSISTANT = "assistant"
    private const val KEY_COMMAND_LANG = "command_lang"
    private const val KEY_RESUME_TIMEOUT = "resume_timeout_s"
    private const val KEY_AUTO_TAP = "auto_tap"
    private const val KEY_SERVICE_ENABLED = "service_enabled"

    // Per-assistant overrides: "<id>_package", "<id>_voice_button_pattern", "<id>_new_chat_pattern"
    // (e.g. "claude_package"). The older un-prefixed regex keys still apply to Claude.
    private const val SUFFIX_PACKAGE = "_package"
    private const val SUFFIX_VOICE_BUTTON_PATTERN = "_voice_button_pattern"
    private const val SUFFIX_NEW_CHAT_PATTERN = "_new_chat_pattern"
    private const val LEGACY_VOICE_BUTTON_PATTERN = "voice_button_pattern"
    private const val LEGACY_NEW_CHAT_PATTERN = "new_chat_pattern"

    const val DEFAULT_THRESHOLD = 0.5f
    const val DEFAULT_RESUME_TIMEOUT_S = 600

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE)

    fun threshold(ctx: Context): Float = sp(ctx).getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD)

    fun setThreshold(ctx: Context, value: Float) {
        sp(ctx).edit().putFloat(KEY_THRESHOLD, value.coerceIn(0.05f, 0.99f)).apply()
    }

    /** The assistant app that the wake word opens (default Claude). */
    fun assistant(ctx: Context): Assistant =
        Assistant.fromId(sp(ctx).getString(KEY_ASSISTANT, null)) ?: Assistant.DEFAULT

    fun setAssistant(ctx: Context, assistant: Assistant) {
        sp(ctx).edit().putString(KEY_ASSISTANT, assistant.id).apply()
    }

    /** Language of the offline voice commands: "de" or "en" (default: system locale). */
    fun commandLang(ctx: Context): String =
        sp(ctx).getString(KEY_COMMAND_LANG, null)?.takeIf { it in CommandParser.LANGS }
            ?: defaultCommandLang()

    fun setCommandLang(ctx: Context, lang: String) {
        require(lang in CommandParser.LANGS) { "unsupported command language '$lang'" }
        sp(ctx).edit().putString(KEY_COMMAND_LANG, lang).apply()
    }

    fun defaultCommandLang(): String = if (Locale.getDefault().language == "de") "de" else "en"

    /** Package of [assistant]; can be overridden with the pref "<id>_package". */
    fun assistantPackage(ctx: Context, assistant: Assistant = assistant(ctx)): String =
        sp(ctx).getString(assistant.id + SUFFIX_PACKAGE, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: assistant.packageName

    fun voiceButtonPattern(ctx: Context, assistant: Assistant = assistant(ctx)): Regex =
        regexOrDefault(override(ctx, assistant, SUFFIX_VOICE_BUTTON_PATTERN, LEGACY_VOICE_BUTTON_PATTERN), assistant.voiceButton)

    fun newChatPattern(ctx: Context, assistant: Assistant = assistant(ctx)): Regex =
        regexOrDefault(override(ctx, assistant, SUFFIX_NEW_CHAT_PATTERN, LEGACY_NEW_CHAT_PATTERN), assistant.newChatButton)

    /** Safety net: listening resumes after this many seconds if no RESUME broadcast arrives. */
    fun resumeTimeoutSec(ctx: Context): Int =
        sp(ctx).getInt(KEY_RESUME_TIMEOUT, DEFAULT_RESUME_TIMEOUT_S).coerceAtLeast(10)

    fun autoTap(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_AUTO_TAP, true)

    /** Threshold for the stop word ("Danke Claude"); separate from the wake word's. */
    fun stopThreshold(ctx: Context): Float = sp(ctx).getFloat("stop_threshold", 0.5f)

    fun setStopThreshold(ctx: Context, value: Float) {
        sp(ctx).edit().putFloat("stop_threshold", value.coerceIn(0.05f, 0.99f)).apply()
    }

    /**
     * Consecutive 80 ms frames the wake score must stay above the threshold. More frames = far fewer
     * false wakes from TV/voices in the room (measured on 10.7 h of real-world audio, see
     * training/evaluate.py) at a small cost in recall.
     */
    const val DEFAULT_TRIGGER_FRAMES = 4

    fun triggerFrames(ctx: Context): Int = sp(ctx).getInt("trigger_frames", DEFAULT_TRIGGER_FRAMES)

    fun setTriggerFrames(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("trigger_frames", value.coerceIn(1, 10)).apply()
    }

    /** Start every wake-word session in a new chat instead of continuing the last one. */
    fun newChatEachSession(ctx: Context): Boolean = sp(ctx).getBoolean("new_chat_each_session", true)

    fun setNewChatEachSession(ctx: Context, value: Boolean) {
        sp(ctx).edit().putBoolean("new_chat_each_session", value).apply()
    }

    /** Skip the neural networks while the room is quiet (see SilenceGate). */
    fun silenceGate(ctx: Context): Boolean = sp(ctx).getBoolean("silence_gate", true)

    /** Saying the wake word during a conversation ends it. */
    fun stopByWakeWord(ctx: Context): Boolean = sp(ctx).getBoolean("stop_by_wake_word", true)

    /** Mute the mic while the assistant speaks (see [EchoGuard]). */
    fun echoGuard(ctx: Context): Boolean = sp(ctx).getBoolean("echo_guard", true)

    /** Whether the user wants the listener running (honoured by BootReceiver). */
    fun serviceEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_SERVICE_ENABLED, true)

    fun setServiceEnabled(ctx: Context, enabled: Boolean) {
        sp(ctx).edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    private fun override(ctx: Context, assistant: Assistant, suffix: String, legacyKey: String): String? {
        val prefs = sp(ctx)
        return prefs.getString(assistant.id + suffix, null)
            ?: if (assistant == Assistant.CLAUDE) prefs.getString(legacyKey, null) else null
    }

    private fun regexOrDefault(stored: String?, default: Regex): Regex {
        if (!stored.isNullOrBlank()) {
            try {
                return Regex(stored)
            } catch (e: Exception) {
                Log.w(Constants.TAG, "Invalid regex in prefs '$stored', using default", e)
            }
        }
        return default
    }
}
