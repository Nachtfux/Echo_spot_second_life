package ai.pocket.heyclaude

object Constants {
    const val PREFS = "hey_claude"

    /** Broadcast (setPackage(ctx.packageName)) => WakeWordService restarts listening. */
    const val ACTION_RESUME = "ai.pocket.heyclaude.RESUME"

    /** Activity intent action that opens MainActivity's mic level test. */
    const val ACTION_MIC_TEST = "ai.pocket.heyclaude.MIC_TEST"

    /**
     * Pre-trained openWakeWord model used until the active assistant's own wake model
     * ([Assistant.wakeModelFile], e.g. hey_claude.onnx) exists: "Alexa" (fits the Echo Spot).
     */
    const val FALLBACK_MODEL_ASSET = "models/alexa_v0.1.onnx"

    /**
     * Status for the Spot clock (apps/clock): broadcast to [CLOCK_PACKAGE] with extras
     * "state" = off | listening | active and "assistant" = display name ("Claude", "ChatGPT").
     * The clock asks with [ACTION_QUERY_STATE].
     */
    const val ACTION_STATE = "ai.pocket.heyclaude.STATE"
    const val ACTION_QUERY_STATE = "ai.pocket.heyclaude.QUERY_STATE"
    const val CLOCK_PACKAGE = "ai.pocket.spotclock"

    /** Log tag used across the app. */
    const val TAG = "HeyClaude"
}
