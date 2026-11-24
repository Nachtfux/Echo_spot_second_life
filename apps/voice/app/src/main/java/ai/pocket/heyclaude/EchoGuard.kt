package ai.pocket.heyclaude

import ai.pocket.heyclaude.launch.VoiceModeA11yService
import ai.pocket.heyclaude.launch.VoiceModeA11yService.Companion.MicResult
import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Runs while a Claude voice session is in front (WakeWordService state PAUSED).
 *
 * 1. Echo guard (half duplex). LineageOS on the Spot has no acoustic echo cancellation and
 *    the Claude app records with VOICE_RECOGNITION, so in hands-free mode Claude hears its
 *    own voice and interrupts itself; the system mic mute is ignored by the Spot's HAL. So
 *    we watch the speaker output (Visualizer on the output mix) and press Claude's
 *    "Mikrofon stummschalten" while it talks, "Mikrofon-Stummschaltung aufheben" right after.
 *    You cannot talk over Claude; a mute set by the user is never undone.
 * 2. Ending the conversation hands-free: "Beenden" is pressed after [IDLE_END_MS] without
 *    anyone talking, or when the transcript shows "Danke Claude" / "Claude Stopp" / ….
 * 3. When the voice-mode screen is gone (ended), the Spot goes home, which makes
 *    WakeWordService listen for the wake word again.
 */
object EchoGuard {
    private const val POLL_MS = 50L
    private const val UI_CHECK_EVERY = 10              // ~500 ms
    private const val VOICE_GONE_CHECKS = 3            // ~1.5 s without voice-mode screen
    private const val RETRY_MS = 300L

    /** Keep the mic closed this long after the output went quiet (pauses between sentences). */
    private const val TAIL_MS = 700L

    /** Output RMS (millibel) above which we treat Claude as speaking. Silence is -9600. */
    private const val RMS_THRESHOLD_MB = -5500

    /** Silence after Claude's last answer (nobody talking) -> "Beenden". */
    private const val IDLE_END_MS = 10_000L

    /** The user spoke last and Claude has not answered yet (thinking) -> allow longer. */
    private const val WAIT_FOR_CLAUDE_MS = 30_000L

    /** Spoken goodbyes, as Claude transcribes them (also catches "Klaud", "Cloud" …). */
    private val STOP_PHRASE = Regex(
        "(?i)\\b(danke(schön)?|stopp?|tschüss?|ciao|ende|beenden|auf wiedersehen|bye)\\b[\\s,.!]{0,3}(claude|claud|klaud|clod|cloud|klod)\\b" +
            "|\\b(claude|claud|klaud|clod|cloud|klod)\\b[\\s,.!]{0,3}(stopp?|danke|tschüss?|ende|beenden|aus)\\b",
    )

    private val handler = Handler(Looper.getMainLooper())
    private var audio: AudioManager? = null
    private var visualizer: Visualizer? = null
    private val peakRms = Visualizer.MeasurementPeakRms()
    private var mic: MicMeter? = null

    private var running = false
    private var ticks = 0
    private var lastLoud = 0L          // Claude's voice on the speaker
    private var lastTranscript = 0L    // new text appeared in Claude's window
    private var mutedByUs = false
    private var lastMuteTry = 0L
    private var voiceSeen = false
    private var voiceSince = 0L
    private var voiceGone = 0
    private var baseline: Set<String> = emptySet()
    private var lastRmsLog = 0L
    private var endRequested = false

    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            tick()
            handler.postDelayed(this, POLL_MS)
        }
    }

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        handler.post {
            if (running || !Prefs.echoGuard(app)) return@post
            audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            visualizer = openVisualizer()
            mic = MicMeter().also { it.start() }
            running = true
            ticks = 0
            lastLoud = 0L
            lastTranscript = 0L
            mutedByUs = false
            voiceSeen = false
            voiceGone = 0
            endRequested = false
            baseline = emptySet()
            handler.post(poll)
            Log.i(Constants.TAG, "EchoGuard on (visualizer=${visualizer != null})")
        }
    }

    fun stop() {
        handler.post {
            if (!running) return@post
            running = false
            handler.removeCallbacks(poll)
            if (mutedByUs) VoiceModeA11yService.setClaudeMic(false)
            mutedByUs = false
            mic?.stop()
            mic = null
            visualizer?.let {
                try {
                    it.enabled = false
                    it.release()
                } catch (_: Exception) {
                }
            }
            visualizer = null
            Log.i(Constants.TAG, "EchoGuard off")
        }
    }

    /** The wake word was heard during the conversation: end it. Main thread. */
    fun endFromWakeWord() {
        if (!running) return
        endConversation("wake word")
    }

    /** Debug/test hook (broadcast MIC_MUTE): press Claude's mic button directly. */
    fun forceMute(on: Boolean) {
        handler.post {
            val r = VoiceModeA11yService.setClaudeMic(on)
            Log.i(Constants.TAG, "EchoGuard: forced mic ${if (on) "mute" else "unmute"} -> $r")
        }
    }

    private fun openVisualizer(): Visualizer? = try {
        Visualizer(0).apply {
            enabled = false
            measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS
            enabled = true
        }
    } catch (e: Throwable) {
        Log.w(Constants.TAG, "EchoGuard: no Visualizer on the output mix, falling back to isMusicActive", e)
        null
    }

    private fun outputLoud(now: Long): Boolean {
        val v = visualizer
        if (v != null) {
            try {
                v.getMeasurementPeakRms(peakRms)
                if (WakeWordService.levelLog && now - lastRmsLog > 500) {
                    lastRmsLog = now
                    Log.d(Constants.TAG, "EchoGuard: out rms=${peakRms.mRms} peak=${peakRms.mPeak} mB")
                }
                return peakRms.mRms > RMS_THRESHOLD_MB
            } catch (e: Exception) {
                Log.w(Constants.TAG, "EchoGuard: Visualizer read failed", e)
            }
        }
        return audio?.isMusicActive == true
    }

    private fun tick() {
        val now = SystemClock.uptimeMillis()
        if (outputLoud(now)) lastLoud = now
        val claudeSpeaking = lastLoud != 0L && now - lastLoud < TAIL_MS

        if (claudeSpeaking && !mutedByUs && now - lastMuteTry > RETRY_MS) {
            lastMuteTry = now
            if (VoiceModeA11yService.setClaudeMic(true) == MicResult.DONE) {
                mutedByUs = true
                Log.i(Constants.TAG, "EchoGuard: Claude speaks -> mic muted")
            }
            // ALREADY: the user muted by hand, leave it. Others: retry while Claude speaks.
        } else if (!claudeSpeaking && mutedByUs) {
            when (VoiceModeA11yService.setClaudeMic(false)) {
                MicResult.DONE, MicResult.ALREADY -> {
                    mutedByUs = false
                    Log.i(Constants.TAG, "EchoGuard: Claude done -> mic open")
                }
                else -> {}
            }
        }

        if (++ticks % UI_CHECK_EVERY == 0) checkVoiceUi(now, claudeSpeaking)
    }

    private fun checkVoiceUi(now: Long, claudeSpeaking: Boolean) {
        when (VoiceModeA11yService.voiceUiVisible()) {
            true -> {
                val texts = VoiceModeA11yService.claudeTexts().orEmpty()
                if (!voiceSeen) {
                    // Session (re)started: earlier transcript lines must not count.
                    voiceSeen = true
                    voiceSince = now
                    baseline = texts.toSet()
                    lastTranscript = 0L
                    endRequested = false
                }
                voiceGone = 0
                val fresh = texts.filter { it !in baseline }
                if (fresh.isNotEmpty()) {
                    lastTranscript = now
                    baseline = baseline + fresh
                    fresh.firstOrNull { STOP_PHRASE.containsMatchIn(it) }?.let {
                        endConversation("stop phrase")
                        return
                    }
                }
                if (!claudeSpeaking && !endRequested) checkIdle(now)
            }
            false -> if (voiceSeen && ++voiceGone >= VOICE_GONE_CHECKS) {
                voiceSeen = false
                voiceGone = 0
                Log.i(Constants.TAG, "EchoGuard: voice mode ended -> home")
                VoiceModeA11yService.forgetEndAction()
                VoiceModeA11yService.goHome()
            }
            null -> {}   // Claude not in front
        }
    }

    /** "Silence" = neither Claude nor the user made a sound, nor did new transcript text appear. */
    private fun checkIdle(now: Long) {
        val m = mic
        val userVoice = if (m != null && !m.silenced) m.lastVoiceAt else 0L
        val lastUser = maxOf(userVoice, lastTranscript)
        val waitingForClaude = lastUser > lastLoud && lastUser > voiceSince
        val since = maxOf(voiceSince, lastLoud, lastUser)
        val limit = if (waitingForClaude) WAIT_FOR_CLAUDE_MS else IDLE_END_MS
        if (now - since > limit) endConversation("${limit / 1000} s silence")
    }

    private fun endConversation(reason: String) {
        endRequested = true
        Log.i(Constants.TAG, "EchoGuard: end conversation ($reason) -> Beenden")
        if (mutedByUs) VoiceModeA11yService.setClaudeMic(false)
        mutedByUs = false
        if (!VoiceModeA11yService.endVoiceMode()) VoiceModeA11yService.goHome()
    }
}
