package ai.pocket.heyclaude

/**
 * Everything that differs between the supported assistant apps: package, the accessibility
 * labels we look for, the names used in spoken stop phrases and the wake/stop model files.
 *
 * Every assistant-specific regex lives here and nowhere else. Label regexes are matched
 * against `contentDescription` (and for the voice button also view id and short texts) of
 * the app's accessibility nodes; use the `DUMP_NODES` broadcast to see the real labels:
 * `adb shell am broadcast -a ai.pocket.heyclaude.DUMP_NODES -p ai.pocket.heyclaude`.
 *
 * Pure Kotlin (no Android classes), so the regexes are covered by JVM unit tests.
 */
enum class Assistant(
    /** Stable id used in prefs, broadcasts (`--es assistant <id>`) and model file names. */
    val id: String,
    val displayName: String,
    val packageName: String,
    /** Voice-mode (speech-to-speech) button in the chat screen; contains-match. */
    val voiceButton: Regex,
    /** Never tapped although they match [voiceButton]: dictation (speech-to-text), read-aloud … */
    val voiceButtonExclude: Regex,
    /** "New chat" button, tried once when no voice button is visible; contains-match. */
    val newChatButton: Regex,
    /** Mic toggle inside voice mode while the mic is open; full match. */
    val micMuteLabel: Regex,
    /** Mic toggle inside voice mode while the mic is muted; contains-match. */
    val micUnmuteLabel: Regex,
    /** Button that ends voice mode; full match. */
    val endButton: Regex,
    /** Action of the app's ongoing voice-mode notification that ends the session; contains-match. */
    val endNotificationAction: Regex,
    /**
     * Regex alternation of the assistant's name as its own transcript spells it, including
     * typical mis-hearings. Used for spoken stop phrases ("danke Claude", "thanks ChatGPT").
     */
    val nameRegex: String,
) {
    // Claude values were calibrated on a real Echo Spot (German UI) — keep them as they are.
    CLAUDE(
        id = "claude",
        displayName = "Claude",
        packageName = "com.anthropic.claude",
        voiceButton = Regex("(?i)voice|sprach|stimm|sound ?wave|audio"),
        // German app: "Spracheingabe starten" (dictation) sits next to "Sprachmodus" (conversation).
        // Also excluded: the "Claude, Sprachantwort" icon next to earlier voice replies in a
        // reopened chat, read-aloud buttons and "mute Claude's voice".
        voiceButtonExclude = Regex(
            "(?i)eingabe|diktier|dictat|voice input|speech.to.text|transcri" +
                "|antwort|response|reply|vorlesen|read aloud|stimme stummschalten|mute claude",
        ),
        newChatButton = Regex("(?i)new chat|neuer chat|start new"),
        // German app: "Mikrofon stummschalten" (mic open) / "Mikrofon-Stummschaltung aufheben"
        // (mic muted). Must not match "Claudes Stimme stummschalten".
        micMuteLabel = Regex("(?i)^(mikrofon stummschalten|mute microphone|mute mic|mute)$"),
        micUnmuteLabel = Regex("(?i)stummschaltung aufheben|^unmute"),
        endButton = Regex("(?i)^(beenden|end|end call|end voice mode)$"),
        endNotificationAction = Regex("(?i)^(beenden|auflegen|end|end call|hang up)$"),
        nameRegex = "claude|claud|klaud|clod|cloud|klod",
    ),

    // ChatGPT values are best-effort guesses for the German and English UI.
    // TODO: calibrate on device via DUMP_NODES (voice button, mute/unmute, end, notification action).
    CHATGPT(
        id = "chatgpt",
        displayName = "ChatGPT",
        packageName = "com.openai.chatgpt",
        // calibrate on device via DUMP_NODES: "Sprachmodus starten" / "Start voice mode" / "Voice mode".
        voiceButton = Regex("(?i)voice[ _-]?mode|sprach[ -]?modus|start voice|use voice|advanced voice|^(voice|sprache)$"),
        // calibrate on device via DUMP_NODES: dictation mic "Diktieren" / "Dictate" / "Spracheingabe"
        // / "Transcribe", plus anything that ends or configures voice mode.
        voiceButtonExclude = Regex(
            "(?i)diktier|diktat|dictat|eingabe|transcri|voice input|speech.to.text|vorlesen|read aloud" +
                "|beenden|\\bend\\b|stop|exit|schließen|close|stumm|mute|einstellung|setting",
        ),
        // calibrate on device via DUMP_NODES
        newChatButton = Regex("(?i)new chat|neuer chat|start new"),
        // calibrate on device via DUMP_NODES: "Mikrofon stummschalten" / "Mute microphone" / "Mute".
        micMuteLabel = Regex("(?i)^(mikrofon stummschalten|stummschalten|mute microphone|mute mic|mute)$"),
        // calibrate on device via DUMP_NODES: "Stummschaltung aufheben" / "Unmute".
        micUnmuteLabel = Regex("(?i)stummschaltung aufheben|^unmute"),
        // calibrate on device via DUMP_NODES: "Beenden" / "End" / "Sprachmodus beenden" /
        // "End voice mode" / "Schließen" / "Close".
        endButton = Regex("(?i)^(beenden|end|sprachmodus beenden|end voice mode|schließen|close)$"),
        // calibrate on device via DUMP_NODES (notification of a running voice session)
        endNotificationAction = Regex("(?i)^(beenden|auflegen|end|end call|hang up|end voice mode|sprachmodus beenden|stop|stopp|stoppen)$"),
        nameRegex = "chat[ -]?g\\.? ?p\\.? ?t|chat[ -]?gbt|tsch[aä]tt? ?g ?p ?t",
    ),
    ;

    /** Wake word model, looked up in getExternalFilesDir(null), then in assets/models/. */
    val wakeModelFile: String get() = "hey_$id.onnx"

    /** Optional stop word model ("Danke Claude"), same lookup as the wake model. */
    val stopModelFile: String get() = "danke_$id.onnx"

    /** Spoken goodbye addressed to this assistant, as its transcript shows it. */
    val stopPhrase: Regex by lazy { StopPhrases.build(nameRegex) }

    /** True for the voice-mode button, false for dictation and the other excluded buttons. */
    fun isVoiceButtonLabel(label: String, pattern: Regex = voiceButton): Boolean =
        pattern.containsMatchIn(label) && !voiceButtonExclude.containsMatchIn(label)

    fun isMicMuteLabel(desc: String): Boolean = micMuteLabel.matches(desc)

    fun isMicUnmuteLabel(desc: String): Boolean = micUnmuteLabel.containsMatchIn(desc)

    fun isEndButtonLabel(desc: String): Boolean = endButton.matches(desc)

    fun isEndNotificationAction(title: String): Boolean = endNotificationAction.containsMatchIn(title)

    fun isStopPhrase(text: String): Boolean = stopPhrase.containsMatchIn(text)

    companion object {
        val DEFAULT = CLAUDE

        /** Profile for an id such as "claude" or "chatgpt" (case-insensitive), or null. */
        fun fromId(id: String?): Assistant? {
            val want = id?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == want }
        }
    }
}

/** Spoken stop phrases in German and English, built around an assistant's name regex. */
internal object StopPhrases {
    /** Words before the name: "danke Claude", "thank you ChatGPT", "bye Claude". */
    private const val BEFORE =
        "danke(schön)?|stopp?|tschüss?|ciao|ende|beenden|auf wiedersehen|bye" +
            "|thank you( (very|so) much)?|thanks( a lot)?|goodbye|good bye|see you"

    /** Words after the name: "Claude stopp", "ChatGPT, bye". */
    private const val AFTER =
        "stopp?|danke|tschüss?|ende|beenden|aus" +
            "|bye|goodbye|that['’]s all|that is all"

    fun build(names: String): Regex = Regex(
        "(?i)\\b($BEFORE)\\b[\\s,.!]{0,3}($names)\\b" +
            "|\\b($names)\\b[\\s,.!]{0,3}($AFTER)\\b",
    )
}
