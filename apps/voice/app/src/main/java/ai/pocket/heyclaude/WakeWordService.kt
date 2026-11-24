package ai.pocket.heyclaude

import ai.pocket.heyclaude.launch.AssistantLauncher
import ai.pocket.heyclaude.command.Action
import ai.pocket.heyclaude.command.CommandParser
import ai.pocket.heyclaude.command.VoiceCommands
import ai.pocket.heyclaude.wake.OpenWakeWordDetector
import ai.pocket.heyclaude.wake.SilenceGate
import ai.pocket.heyclaude.wake.WakeWordDetector
import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.KeyEvent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Foreground service that listens for the wake word.
 *
 * Loop (background thread): open AudioRecord -> feed frames of
 * detector.frameSamples -> on Prefs.triggerFrames consecutive frames above the threshold:
 * release the mic, reset the detector, launch the assistant app (Claude or ChatGPT,
 * [Prefs.assistant]) -> wait for [Constants.ACTION_RESUME] (or the resume timeout) -> listen again.
 *
 * [ACTION_SET_ASSISTANT] / [ACTION_SET_LANG] switch the assistant (reloading the wake and stop
 * models) and the command language (reloading the Vosk model) without a reinstall.
 */
class WakeWordService : Service() {

    enum class State { STOPPED, STARTING, LISTENING, PAUSED, ERROR }

    companion object {
        const val ACTION_STOP = "ai.pocket.heyclaude.STOP"
        const val ACTION_TRIGGER_NOW = "ai.pocket.heyclaude.TRIGGER_NOW"
        const val ACTION_MIC_MUTE = "ai.pocket.heyclaude.MIC_MUTE"
        const val ACTION_LEVEL_LOG = "ai.pocket.heyclaude.LEVEL_LOG"
        const val ACTION_COMMAND = "ai.pocket.heyclaude.COMMAND"
        /** More words than this after the wake word, without a command = false alarm. */
        private const val FALSE_ALARM_WORDS = 2
        @Volatile var falseAlarms = 0
        const val ACTION_SET_THRESHOLD = "ai.pocket.heyclaude.SET_THRESHOLD"
        /** `--es assistant claude|chatgpt` */
        const val ACTION_SET_ASSISTANT = "ai.pocket.heyclaude.SET_ASSISTANT"
        /** `--es lang de|en` */
        const val ACTION_SET_LANG = "ai.pocket.heyclaude.SET_LANG"

        /** Debug: log the mic level about twice a second (toggle via ACTION_LEVEL_LOG). */
        @Volatile var levelLog = false

        private const val CHANNEL_ID = "listening"
        private const val NOTIFICATION_ID = 1
        private const val SAMPLE_RATE = 16_000
        /** Ignore triggers for the first frames after (re)start: ~1 s @ 80 ms. */
        private const val WARMUP_FRAMES = 12
        /** All-zero audio for this long => the OS is probably silencing us. */
        private const val SILENCE_WARN_MS = 5_000L
        private const val MAX_BACKOFF_MS = 30_000L

        // ---- State for MainActivity (polled from the UI thread) ----
        @Volatile var running = false
            private set
        @Volatile var state = State.STOPPED
            private set
        @Volatile var modelName: String? = null
            private set
        @Volatile var lastScore = 0f
            private set
        @Volatile var peakScore = 0f
            private set
        @Volatile var micLevel = 0f
            private set
        @Volatile var micSilenced = false
            private set
        @Volatile var lastError: String? = null
            private set
        @Volatile var triggerCount = 0
            private set
        @Volatile var lastTriggerAt = 0L
            private set

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, WakeWordService::class.java))
            } catch (e: Exception) {
                Log.e(Constants.TAG, "Could not start WakeWordService", e)
                lastError = ctx.getString(R.string.err_start_failed, e.message)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, WakeWordService::class.java))
        }

        /**
         * Persists a [ACTION_SET_ASSISTANT] / [ACTION_SET_LANG] intent to [Prefs]. Returns false
         * (and logs why) for unknown values. Also used by [SettingsReceiver] while the service is
         * not running.
         */
        fun saveSetting(ctx: Context, intent: Intent): Boolean {
            when (intent.action) {
                ACTION_SET_ASSISTANT -> {
                    val raw = intent.getStringExtra("assistant")
                    val assistant = Assistant.fromId(raw)
                    if (assistant == null) {
                        Log.w(Constants.TAG, "SET_ASSISTANT: unknown assistant '$raw' (${Assistant.entries.joinToString("|") { it.id }})")
                        return false
                    }
                    Prefs.setAssistant(ctx, assistant)
                    Log.i(Constants.TAG, "Assistant -> ${assistant.id} (${Prefs.assistantPackage(ctx, assistant)})")
                    return true
                }
                ACTION_SET_LANG -> {
                    val lang = intent.getStringExtra("lang")?.trim()?.lowercase()
                    if (lang == null || lang !in CommandParser.LANGS) {
                        Log.w(Constants.TAG, "SET_LANG: unsupported language '$lang' (${CommandParser.LANGS.joinToString("|")})")
                        return false
                    }
                    Prefs.setCommandLang(ctx, lang)
                    Log.i(Constants.TAG, "Command language -> $lang (${VoiceCommands.modelDir(lang)})")
                    return true
                }
            }
            return false
        }

        /** Same path as a detected wake word (releases the mic first). */
        fun triggerNow(ctx: Context) {
            val i = Intent(ctx, WakeWordService::class.java).setAction(ACTION_TRIGGER_NOW)
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) {
                Log.e(Constants.TAG, "triggerNow failed", e)
            }
        }
    }

    private val lock = java.lang.Object()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var stopRequested = false
    @Volatile private var paused = false
    @Volatile private var resumeRequested = false
    @Volatile private var manualTrigger = false
    /** Set by SET_ASSISTANT: the worker reloads the wake + stop models of the active assistant. */
    @Volatile private var reloadRequested = false
    @Volatile private var pendingCommand: String? = null
    @Volatile private var musicWasPlaying = false
    private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var receiverRegistered = false

    private val resumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Constants.ACTION_RESUME -> requestResume("broadcast")
                Constants.ACTION_QUERY_STATE -> sendClockState()
                // Test hook: adb shell am broadcast -a ai.pocket.heyclaude.MIC_MUTE --ez on true
                ACTION_MIC_MUTE -> EchoGuard.forceMute(intent.getBooleanExtra("on", false))
                ACTION_LEVEL_LOG -> levelLog = intent.getBooleanExtra("on", false)
                // Setup hook: adb shell am broadcast -a ai.pocket.heyclaude.SET_THRESHOLD --ef wake 0.7 --ef stop 0.5
                ACTION_SET_THRESHOLD -> {
                    if (intent.hasExtra("wake")) Prefs.setThreshold(context, intent.getFloatExtra("wake", 0.5f))
                    if (intent.hasExtra("stop")) Prefs.setStopThreshold(context, intent.getFloatExtra("stop", 0.5f))
                    if (intent.hasExtra("frames")) Prefs.setTriggerFrames(context, intent.getIntExtra("frames", Prefs.DEFAULT_TRIGGER_FRAMES))
                    if (intent.hasExtra("new_chat")) Prefs.setNewChatEachSession(context, intent.getBooleanExtra("new_chat", true))
                    Log.i(Constants.TAG, "Thresholds: wake=${Prefs.threshold(context)} stop=${Prefs.stopThreshold(context)} frames=${Prefs.triggerFrames(context)} newChat=${Prefs.newChatEachSession(context)}")
                }
                // Test hook: adb shell am broadcast -a ai.pocket.heyclaude.COMMAND --es text "spiel queen"
                ACTION_COMMAND -> {
                    val text = intent.getStringExtra("text").orEmpty()
                    val action = VoiceCommands.parse(text)
                    Log.i(Constants.TAG, "Test command '$text' (${VoiceCommands.parser.lang}) -> $action")
                    if (action != null) VoiceCommands.execute(this@WakeWordService, action)
                }
                // Setup hook: adb shell am broadcast -a ai.pocket.heyclaude.SET_ASSISTANT -p ai.pocket.heyclaude --es assistant chatgpt
                ACTION_SET_ASSISTANT -> if (saveSetting(context, intent)) reloadModels()
                // Setup hook: adb shell am broadcast -a ai.pocket.heyclaude.SET_LANG -p ai.pocket.heyclaude --es lang en
                ACTION_SET_LANG -> if (saveSetting(context, intent)) {
                    VoiceCommands.setLanguage(context, Prefs.commandLang(context))
                }
            }
        }
    }

    /** Assistant switched: the worker reloads the wake and stop models (see [workerLoop]). */
    private fun reloadModels() {
        Log.i(Constants.TAG, "Reloading wake/stop models for ${Prefs.assistant(this).id}")
        reloadRequested = true
        synchronized(lock) { lock.notifyAll() }
        sendClockState()
    }

    /** Tells the Spot clock whether we listen (orange centre) or the assistant is talking (pulse). */
    private fun sendClockState(s: State = state) {
        val value = when (s) {
            State.LISTENING -> "listening"
            State.PAUSED -> "active"
            else -> "off"
        }
        try {
            sendBroadcast(
                Intent(Constants.ACTION_STATE)
                    .setPackage(Constants.CLOCK_PACKAGE)
                    .putExtra("state", value)
                    .putExtra("assistant", Prefs.assistant(this).displayName),
            )
        } catch (e: Exception) {
            Log.w(Constants.TAG, "clock state broadcast failed", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        goForeground(getString(R.string.notif_starting))
        // Dynamic receivers are fine without export flags at targetSdk 30;
        // left exported so `adb shell am broadcast -a ai.pocket.heyclaude.RESUME` works for debugging.
        registerReceiver(
            resumeReceiver,
            IntentFilter(Constants.ACTION_RESUME).apply {
                addAction(Constants.ACTION_QUERY_STATE)
                addAction(ACTION_MIC_MUTE)
                addAction(ACTION_LEVEL_LOG)
                addAction(ACTION_COMMAND)
                addAction(ACTION_SET_THRESHOLD)
                addAction(ACTION_SET_ASSISTANT)
                addAction(ACTION_SET_LANG)
            },
        )
        receiverRegistered = true
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeyClaude:listen").apply {
            setReferenceCounted(false)
            acquire()
        }
        running = true
        lastError = null
        setState(State.STARTING)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every startForegroundService() must be answered with startForeground().
        goForeground(notificationText())
        when (intent?.action) {
            ACTION_STOP -> {
                Prefs.setServiceEnabled(this, false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_NOW -> {
                if (paused) launchAssistant() else manualTrigger = true
            }
        }
        ensureWorker()
        return START_STICKY
    }

    override fun onDestroy() {
        stopRequested = true
        synchronized(lock) { lock.notifyAll() }
        worker?.let {
            it.interrupt()
            try {
                it.join(1500)
            } catch (_: InterruptedException) {
            }
        }
        worker = null
        if (receiverRegistered) {
            try {
                unregisterReceiver(resumeReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        mainHandler.removeCallbacksAndMessages(null)
        running = false
        lastScore = 0f
        micLevel = 0f
        state = State.STOPPED
        EchoGuard.stop()
        sendClockState(State.STOPPED)
        Log.i(Constants.TAG, "WakeWordService destroyed")
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Worker
    // ------------------------------------------------------------------

    private fun ensureWorker() {
        if (worker?.isAlive == true) return
        stopRequested = false
        worker = Thread({ workerLoop() }, "HeyClaude-listen").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun workerLoop() {
        var detector: WakeWordDetector? = null
        var backoffMs = 1_000L
        try {
            while (!stopRequested) {
                if (reloadRequested) {
                    // SET_ASSISTANT: drop the models; they are loaded again for the new assistant below.
                    reloadRequested = false
                    try {
                        detector?.close()
                    } catch (e: Throwable) {
                        Log.w(Constants.TAG, "Detector close failed", e)
                    }
                    detector = null
                    stopModelMissingLogged = false
                }
                if (detector == null) {
                    detector = try {
                        val assistant = Prefs.assistant(this)
                        OpenWakeWordDetector(this, assistant.wakeModelFile).also {
                            modelName = it.modelName
                            Log.i(Constants.TAG, "Detector ready for ${assistant.id}, model=${it.modelName}")
                            VoiceCommands.preload(this)
                        }
                    } catch (e: Throwable) {
                        Log.e(Constants.TAG, "Detector init failed", e)
                        fail(getString(R.string.err_model, e.message))
                        sleepInterruptibly(backoffMs)
                        backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
                        continue
                    }
                }

                if (paused) {
                    if (Prefs.stopByWakeWord(this)) listenForStop(detector) else waitForResume()
                    continue
                }

                when (listen(detector)) {
                    ListenResult.TRIGGERED -> {
                        backoffMs = 1_000L
                        detector.reset()
                        val cmd = pendingCommand
                        pendingCommand = null
                        val action = cmd?.let { VoiceCommands.parse(it) }
                        val words = cmd?.split(Regex("\\s+"))?.count { it.isNotBlank() } ?: 0
                        when {
                            action != null -> runVoiceCommand(cmd, action)
                            // A real "Hey Claude" is followed by a pause (you wait for the tone). Ongoing
                            // talk that is no command means the wake word fired on room conversation.
                            words > FALSE_ALARM_WORDS -> ignoreFalseAlarm(cmd!!)
                            else -> {
                                if (!cmd.isNullOrEmpty()) Log.i(Constants.TAG, "Short reply '$cmd' -> assistant")
                                launchAssistant()
                            }
                        }
                    }
                    ListenResult.MIC_ERROR -> {
                        Log.w(Constants.TAG, "Mic error, retry in ${backoffMs} ms")
                        sleepInterruptibly(backoffMs)
                        backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
                    }
                    ListenResult.HEALTHY_EXIT -> backoffMs = 1_000L
                    ListenResult.STOPPED -> Unit
                }
            }
        } finally {
            try {
                detector?.close()
            } catch (e: Throwable) {
                Log.w(Constants.TAG, "Detector close failed", e)
            }
        }
    }

    private enum class ListenResult { TRIGGERED, MIC_ERROR, HEALTHY_EXIT, STOPPED }

    @SuppressLint("MissingPermission")
    private fun openRecorder(): AudioRecord? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            fail(getString(R.string.err_mic_permission))
            return null
        }
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) {
            fail("AudioRecord.getMinBufferSize = $minBuf")
            return null
        }
        val bufBytes = maxOf(minBuf, SAMPLE_RATE * 4) // >= 2 s of audio (room for the SilenceGate pre-roll burst)
        for (source in intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            try {
                val rec = AudioRecord(
                    source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufBytes,
                )
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    rec.startRecording()
                    if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        Log.i(Constants.TAG, "AudioRecord started (source=$source, buf=$bufBytes)")
                        return rec
                    }
                    Log.w(Constants.TAG, "startRecording failed for source $source")
                    rec.stop()
                }
                rec.release()
            } catch (e: Exception) {
                Log.w(Constants.TAG, "AudioRecord source $source failed", e)
            }
        }
        fail(getString(R.string.err_mic_open))
        return null
    }

    private fun listen(detector: WakeWordDetector): ListenResult {
        val rec = openRecorder() ?: return ListenResult.MIC_ERROR
        val frame = ShortArray(detector.frameSamples)
        var consecutive = 0
        var frames = 0
        var silentSinceMs = -1L
        val startedAt = SystemClock.elapsedRealtime()
        detector.reset()
        lastError = null
        micSilenced = false
        peakScore = 0f
        setState(State.LISTENING)
        val gate = SilenceGate(frame.size).also { this.gate = it }
        try {
            while (!stopRequested && !paused && !reloadRequested) {
                if (!readFully(rec, frame)) {
                    fail(getString(R.string.err_mic_read))
                    // A long healthy run before the error: retry quickly.
                    return if (SystemClock.elapsedRealtime() - startedAt > 10_000) {
                        ListenResult.HEALTHY_EXIT
                    } else {
                        ListenResult.MIC_ERROR
                    }
                }
                frames++

                // Level + "silenced by OS" detection.
                var peak = 0
                var sumSq = 0.0
                for (s in frame) {
                    val a = abs(s.toInt())
                    if (a > peak) peak = a
                    sumSq += (s * s).toDouble()
                }
                micLevel = (sqrt(sumSq / frame.size) / 32768.0).toFloat()
                if (levelLog && frames % 25 == 0) Log.d(Constants.TAG, "level=%.4f peak=%d".format(micLevel, peak))
                val now = SystemClock.elapsedRealtime()
                if (peak == 0) {
                    if (silentSinceMs < 0) silentSinceMs = now
                    if (!micSilenced && now - silentSinceMs > SILENCE_WARN_MS) {
                        micSilenced = true
                        Log.w(Constants.TAG, "Mic delivers only zeros — capture probably silenced by the OS")
                    }
                } else {
                    silentSinceMs = -1L
                    micSilenced = false
                }

                // Quiet room: skip the neural networks (see SilenceGate).
                val gated = gate.push(frame, micLevel, Prefs.silenceGate(this))
                if (gated.wokeUp) detector.reset()
                var score = 0f
                try {
                    for (f in gated.frames) score = detector.process(f)
                } catch (e: Throwable) {
                    Log.e(Constants.TAG, "Detector process failed", e)
                    fail(getString(R.string.err_detect, e.message))
                    detector.reset()
                    return ListenResult.MIC_ERROR
                }
                if (frames % 3750 == 0) logGate("wake")   // every ~5 min
                lastScore = score
                if (score > peakScore) peakScore = score

                if (manualTrigger) {
                    manualTrigger = false
                    Log.i(Constants.TAG, "Manual trigger")
                    return ListenResult.TRIGGERED
                }

                val threshold = Prefs.threshold(this)
                consecutive = if (frames > WARMUP_FRAMES && score > threshold) consecutive + 1 else 0
                if (consecutive >= Prefs.triggerFrames(this)) {
                    Log.i(Constants.TAG, "Wake word detected (score=$score, threshold=$threshold, frames=$consecutive)")
                    triggerCount++
                    lastTriggerAt = System.currentTimeMillis()
                    pendingCommand = captureCommand(rec, frame)
                    return ListenResult.TRIGGERED
                }
            }
            return if (stopRequested) ListenResult.STOPPED else ListenResult.HEALTHY_EXIT
        } finally {
            try {
                rec.stop()
            } catch (_: Exception) {
            }
            rec.release()
            micLevel = 0f
            lastScore = 0f
        }
    }

    private fun readFully(rec: AudioRecord, buf: ShortArray): Boolean {
        var off = 0
        while (off < buf.size) {
            if (stopRequested) return true
            val n = rec.read(buf, off, buf.size - off)
            if (n < 0) {
                Log.w(Constants.TAG, "AudioRecord.read returned $n")
                return false
            }
            off += n
        }
        return true
    }

    // ------------------------------------------------------------------
    // Pause / resume
    // ------------------------------------------------------------------

    /** Called on the worker thread after the mic has been released. */
    private fun launchAssistant() {
        paused = true
        resumeRequested = false
        setState(State.PAUSED)
        mainHandler.post {
            val ok = try {
                AssistantLauncher.launch(this)
            } catch (e: Exception) {
                Log.e(Constants.TAG, "AssistantLauncher.launch threw", e)
                false
            }
            if (!ok) {
                lastError = getString(R.string.assistant_missing, Prefs.assistant(this).displayName, Prefs.assistantPackage(this))
                Log.w(Constants.TAG, lastError!!)
                mainHandler.postDelayed({ requestResume("launch failed") }, 2_000)
            }
        }
    }

    private fun requestResume(reason: String) {
        if (!paused) return
        Log.i(Constants.TAG, "Resume requested ($reason)")
        resumeRequested = true
        synchronized(lock) { lock.notifyAll() }
    }

    private fun waitForResume() {
        val timeoutMs = Prefs.resumeTimeoutSec(this) * 1000L
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        synchronized(lock) {
            while (!stopRequested && !resumeRequested) {
                val left = deadline - SystemClock.elapsedRealtime()
                if (left <= 0) {
                    Log.i(Constants.TAG, "No RESUME within ${timeoutMs / 1000} s — resuming anyway")
                    break
                }
                try {
                    lock.wait(left)
                } catch (_: InterruptedException) {
                    if (stopRequested) return
                }
            }
        }
        resumeRequested = false
        paused = false
        // Give the assistant app a moment to release the microphone.
        sleepInterruptibly(500)
    }

    /**
     * While an assistant conversation runs (paused), keep listening for the wake word: saying it
     * again (or the stop word) ends the conversation (EchoGuard presses the end button). We hear
     * the room even while EchoGuard has muted the assistant's own mic, so this also works while
     * the assistant is talking.
     */
    private fun listenForStop(detector: WakeWordDetector) {
        val timeoutMs = Prefs.resumeTimeoutSec(this) * 1000L
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        val rec = openRecorder()
        if (rec == null) {
            setState(State.PAUSED)          // openRecorder() reported ERROR; the session still runs
            waitForResume()
            return
        }
        val frame = ShortArray(detector.frameSamples)
        var frames = 0
        var consecutive = 0
        var cooldownUntil = 0L
        var readOk = true
        detector.reset()
        val owd = detector as? OpenWakeWordDetector
        val stopModel = Prefs.assistant(this).stopModelFile
        // Stop word: consecutive frames above its threshold, per assistant (the wake word uses Prefs.triggerFrames).
        val stopFrames = Prefs.assistant(this).stopFrames
        val hasStop = owd?.attachExtra(this, stopModel) == true
        if (!hasStop && !stopModelMissingLogged) {
            stopModelMissingLogged = true
            Log.i(Constants.TAG, "No stop word model ($stopModel); the wake word ends conversations")
        }
        val stopGate = SilenceGate(frame.size)
        try {
            while (!stopRequested && !resumeRequested && !reloadRequested) {
                if (SystemClock.elapsedRealtime() > deadline) {
                    Log.i(Constants.TAG, "No RESUME within ${timeoutMs / 1000} s — resuming anyway")
                    break
                }
                if (!readFully(rec, frame)) {
                    readOk = false
                    break
                }
                frames++
                var sumSq = 0.0
                for (s in frame) sumSq += (s * s).toDouble()
                val rms = (sqrt(sumSq / frame.size) / 32768.0).toFloat()
                val gated = stopGate.push(frame, rms, Prefs.silenceGate(this))
                if (gated.wokeUp) detector.reset()
                val score = try {
                    // Wake word again or the stop word ("Danke Claude") end the conversation;
                    // the stop model scores the wake pipeline's embeddings (no second mel/embedding run).
                    // Normalised: >1 means "over its own threshold" (wake and stop word have separate ones).
                    var s = 0f
                    val wakeTh = Prefs.threshold(this)
                    val stopTh = Prefs.stopThreshold(this)
                    for (f in gated.frames) {
                        s = detector.process(f) / wakeTh
                        if (hasStop) s = max(s, owd!!.extraScore() / stopTh)
                    }
                    s
                } catch (e: Throwable) {
                    Log.e(Constants.TAG, "Detector process failed (stop listener)", e)
                    readOk = false
                    break
                }
                val now = SystemClock.elapsedRealtime()
                val hot = frames > WARMUP_FRAMES && now > cooldownUntil && score > 1f
                consecutive = if (hot) consecutive + 1 else 0
                if (consecutive >= stopFrames) {
                    Log.i(Constants.TAG, "Wake/stop word during conversation (score=$score) -> end it")
                    consecutive = 0
                    cooldownUntil = now + 4_000
                    detector.reset()
                    mainHandler.post { EchoGuard.endFromWakeWord() }
                }
            }
        } finally {
            try {
                rec.stop()
            } catch (_: Exception) {
            }
            rec.release()
        }
        if (stopRequested) return
        // SET_ASSISTANT during a conversation: stay paused; the worker reloads and comes back here.
        if (reloadRequested && !resumeRequested) return
        if (!readOk && !resumeRequested) {
            waitForResume()                 // mic trouble: fall back to plain waiting
            return
        }
        resumeRequested = false
        paused = false
        sleepInterruptibly(500)
    }

    /**
     * Right after the wake word, with the mic still open: a short music command?
     * Returns null when voice commands are off (no Vosk model), "" when nothing was said.
     */
    private fun captureCommand(rec: AudioRecord, frame: ShortArray): String? {
        if (!VoiceCommands.ready) return null
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        musicWasPlaying = audio.isMusicActive
        // Pause our own music so the command is understood (resumed after volume/skip commands).
        if (musicWasPlaying) VoiceCommands.mediaKey(audio, KeyEvent.KEYCODE_MEDIA_PAUSE)
        sendClockState(State.PAUSED)                       // clock pulses while we listen
        VoiceCommands.tone(ToneGenerator.TONE_PROP_BEEP)
        val text = try {
            VoiceCommands.recognize(frame, { readFully(rec, it) }, { stopRequested })
        } catch (e: Throwable) {
            Log.e(Constants.TAG, "Command recognition failed", e)
            ""
        }
        Log.i(Constants.TAG, "Command heard: '$text'")
        return text
    }

    private fun ignoreFalseAlarm(text: String) {
        Log.i(Constants.TAG, "No command in '$text' -> probably a false alarm, not opening the assistant")
        falseAlarms++
        VoiceCommands.tone(ToneGenerator.TONE_PROP_NACK)
        if (musicWasPlaying) {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            mainHandler.postDelayed({ VoiceCommands.mediaKey(audio, KeyEvent.KEYCODE_MEDIA_PLAY) }, 300)
        }
    }

    private fun runVoiceCommand(text: String, action: Action) {
        Log.i(Constants.TAG, "Voice command '$text' -> $action")
        val resume = musicWasPlaying && (action is Action.Volume ||
            action is Action.Next || action is Action.Previous)
        mainHandler.post {
            VoiceCommands.execute(this, action)
            if (resume) {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                mainHandler.postDelayed({ VoiceCommands.mediaKey(audio, KeyEvent.KEYCODE_MEDIA_PLAY) }, 400)
            }
        }
    }

    private var stopModelMissingLogged = false
    private var gate: SilenceGate? = null

    private fun logGate(where: String) {
        val g = gate ?: return
        if (g.totalFrames == 0L) return
        Log.i(Constants.TAG, "SilenceGate($where): networks ran for %d%% of frames".format(100 * g.processedFrames / g.totalFrames))
    }

    private fun sleepInterruptibly(ms: Long) {
        val deadline = SystemClock.elapsedRealtime() + ms
        synchronized(lock) {
            while (!stopRequested) {
                val left = deadline - SystemClock.elapsedRealtime()
                if (left <= 0) return
                try {
                    lock.wait(left)
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // State + notification
    // ------------------------------------------------------------------

    private fun fail(msg: String) {
        lastError = msg
        setState(State.ERROR)
    }

    private fun setState(s: State) {
        if (state == s) return
        state = s
        Log.i(Constants.TAG, "State -> $s")
        sendClockState(s)
        // PAUSED = assistant session in front: guard against the assistant hearing itself.
        if (s == State.PAUSED) EchoGuard.start(this) else EchoGuard.stop()
        mainHandler.post {
            if (running) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(notificationText()))
            }
        }
    }

    private fun notificationText(): String = when (state) {
        State.LISTENING -> getString(R.string.notif_listening)
        State.PAUSED -> getString(R.string.notif_paused, Prefs.assistant(this).displayName)
        State.ERROR -> lastError ?: getString(R.string.notif_error)
        else -> getString(R.string.notif_starting)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_spark)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notif_action_stop), stopIntent)
            .build()
    }

    private fun goForeground(text: String) {
        val n = buildNotification(text)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
    }
}
