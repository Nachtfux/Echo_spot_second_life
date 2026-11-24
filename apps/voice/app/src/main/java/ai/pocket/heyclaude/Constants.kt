package ai.pocket.heyclaude

object Constants {
    const val PREFS = "hey_claude"

    /** Broadcast (setPackage(ctx.packageName)) => WakeWordService restarts listening. */
    const val ACTION_RESUME = "ai.pocket.heyclaude.RESUME"

    /** Activity intent action that opens MainActivity's mic level test. */
    const val ACTION_MIC_TEST = "ai.pocket.heyclaude.MIC_TEST"

    const val DEFAULT_CLAUDE_PACKAGE = "com.anthropic.claude"

    /** Looked up in context.getExternalFilesDir(null) first, then assets/models/. */
    const val CUSTOM_MODEL_FILE = "hey_claude.onnx"

    /** Pre-trained openWakeWord model used until a custom hey_claude.onnx exists: "Alexa" (fits the Echo Spot). */
    const val FALLBACK_MODEL_ASSET = "models/alexa_v0.1.onnx"

    /** Optional stop word ("Danke Claude"): ends a running conversation. Same lookup as the wake model. */
    const val STOP_MODEL_FILE = "danke_claude.onnx"

    /**
     * Status for the Spot clock (android/spot-clock): broadcast to [CLOCK_PACKAGE] with
     * extra "state" = off | listening | active. The clock asks with [ACTION_QUERY_STATE].
     */
    const val ACTION_STATE = "ai.pocket.heyclaude.STATE"
    const val ACTION_QUERY_STATE = "ai.pocket.heyclaude.QUERY_STATE"
    const val CLOCK_PACKAGE = "ai.pocket.spotclock"

    /** Log tag used across the app. */
    const val TAG = "HeyClaude"
}
