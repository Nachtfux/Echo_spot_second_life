package ai.pocket.heyclaude.command

import ai.pocket.heyclaude.Constants
import ai.pocket.heyclaude.Prefs
import ai.pocket.heyclaude.launch.VoiceModeA11yService
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.Locale

/**
 * Short commands right after the wake word, recognised OFFLINE on the Spot with Vosk.
 * The model of the command language ([Prefs.commandLang]) is pushed via adb to
 * getExternalFilesDir(null)/[modelDir] ("vosk-de" = vosk-model-small-de, "vosk-en" =
 * vosk-model-small-en-us); the text is parsed by [GermanParser] or [EnglishParser]:
 *   music  – "spiel Queen" / "play Queen", "Pause", "weiter" / "resume", "nächstes Lied" / "next"
 *   radio  – "spiel SWR3", "Radio Bayern drei" / "radio BBC one", "Radio aus" / "radio off"
 *   timer  – "Timer zehn Minuten" / "set a timer for ten minutes"; alarm – "Wecker um halb sieben" /
 *            "wake me up at half past six"
 * Anything else goes to the assistant. Timers/alarms live in the Spot clock app (broadcasts below).
 */
object VoiceCommands {
    private const val SPOTIFY = "com.spotify.music"
    private const val CLOCK = "ai.pocket.spotclock"
    private const val SAMPLE_RATE = 16000f

    /** Guards [model] while [recognize] uses it, so a language switch never closes it mid-use. */
    private val modelLock = Any()
    @Volatile private var model: Model? = null
    @Volatile private var modelLang: String? = null
    @Volatile private var loading = false
    private val main = Handler(Looper.getMainLooper())

    /** Parser of the current command language (set by [preload] / [setLanguage]). */
    @Volatile var parser: CommandParser = GermanParser
        private set

    /** Vosk model folder of a command language, inside getExternalFilesDir(null). */
    fun modelDir(lang: String): String = "vosk-$lang"

    /** Loads the Vosk model of [Prefs.commandLang] in the background (a few seconds on the Spot). */
    fun preload(ctx: Context) {
        val app = ctx.applicationContext
        val lang = Prefs.commandLang(app)
        parser = CommandParser.forLang(lang)
        if ((model != null && modelLang == lang) || loading) return
        val dir = File(app.getExternalFilesDir(null), modelDir(lang))
        if (!File(dir, "am").isDirectory) {
            Log.i(Constants.TAG, "Voice commands off: no Vosk model in $dir")
            return
        }
        loading = true
        Thread({
            try {
                val t0 = SystemClock.elapsedRealtime()
                val loaded = Model(dir.absolutePath)
                val old = synchronized(modelLock) {
                    model.also {
                        model = loaded
                        modelLang = lang
                    }
                }
                old?.close()
                Log.i(Constants.TAG, "Vosk model ${modelDir(lang)} ready in ${SystemClock.elapsedRealtime() - t0} ms")
            } catch (e: Throwable) {
                Log.e(Constants.TAG, "Vosk model ${modelDir(lang)} failed to load", e)
            } finally {
                loading = false
            }
            // The language changed while we were loading: load the right model now.
            if (Prefs.commandLang(app) != lang) preload(app)
        }, "vosk-load").start()
    }

    /** Switches the command language (Prefs + parser) and loads that language's Vosk model. */
    fun setLanguage(ctx: Context, lang: String) {
        val app = ctx.applicationContext
        Prefs.setCommandLang(app, lang)
        parser = CommandParser.forLang(lang)
        if (modelLang == lang) {
            preload(app)
            return
        }
        Thread({
            // Drop the old model first: it would recognise the wrong language. Off the main
            // thread because this waits for a recognize() that is still running.
            val old = synchronized(modelLock) {
                model.also {
                    model = null
                    modelLang = null
                }
            }
            old?.close()
            preload(app)
        }, "vosk-switch").start()
    }

    val ready: Boolean get() = model != null

    /**
     * Listens for one short utterance after the wake word. [read] fills the frame and returns
     * false on mic errors. Returns "" if nothing was said (a plain "Hey Claude").
     */
    fun recognize(frame: ShortArray, read: (ShortArray) -> Boolean, stop: () -> Boolean): String {
        synchronized(modelLock) {
            val m = model ?: return ""
            val rec = Recognizer(m, SAMPLE_RATE)
            try {
                val start = SystemClock.elapsedRealtime()
                var spoke = false
                while (!stop()) {
                    if (!read(frame)) break
                    val now = SystemClock.elapsedRealtime()
                    if (rec.acceptWaveForm(frame, frame.size)) {
                        val text = textOf(rec.result, "text")
                        if (text.isNotEmpty()) return text
                    } else if (!spoke && textOf(rec.partialResult, "partial").isNotEmpty()) {
                        spoke = true
                    }
                    if (!spoke && now - start > 2_000) return ""   // nothing said after the wake word
                    if (now - start > 7_000) break                   // cap long utterances
                }
                return textOf(rec.finalResult, "text")
            } finally {
                rec.close()
            }
        }
    }

    private fun textOf(json: String, key: String): String =
        try {
            JSONObject(json).optString(key, "").trim()
        } catch (_: Exception) {
            ""
        }

    // ------------------------------------------------------------- parsing --

    /** Parses with the current command language's [CommandParser]. */
    fun parse(raw: String): Action? = parser.parse(raw)

    // ----------------------------------------------------------- executing --

    /** Runs on the main thread. */
    fun execute(ctx: Context, action: Action) {
        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (action) {
            is Action.PlaySearch -> {
                // A station with exactly this name ("SWR3") -> radio, else Spotify.
                Thread({
                    val station = RadioBrowser.find(parser.stationQuery(action.query), strict = true, radioCountry())
                    main.post {
                        if (station != null) playRadio(ctx, station) else spotify(ctx, action.query)
                    }
                }, "radio-lookup").start()
                return
            }
            is Action.Radio -> {
                Thread({
                    val station = RadioBrowser.find(parser.stationQuery(action.station), strict = false, radioCountry())
                    main.post {
                        if (station != null) playRadio(ctx, station)
                        else {
                            Log.i(Constants.TAG, "No station for '${action.station}'")
                            tone(ToneGenerator.TONE_PROP_NACK)
                        }
                    }
                }, "radio-lookup").start()
                return
            }
            Action.RadioStop -> RadioService.stop(ctx)
            Action.Play -> mediaKey(audio, KeyEvent.KEYCODE_MEDIA_PLAY)
            Action.Pause -> {
                clock(ctx, Intent("$CLOCK.STOP_RINGING"))       // "stopp" also ends a ringing timer
                mediaKey(audio, KeyEvent.KEYCODE_MEDIA_PAUSE)
            }
            Action.Next -> mediaKey(audio, KeyEvent.KEYCODE_MEDIA_NEXT)
            Action.Previous -> mediaKey(audio, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            is Action.Volume -> repeat(kotlin.math.abs(action.steps)) {
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (action.steps > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI,
                )
            }
            is Action.Timer -> clock(ctx, Intent("$CLOCK.TIMER").putExtra("seconds", action.seconds))
            Action.TimerCancel -> clock(ctx, Intent("$CLOCK.TIMER").putExtra("seconds", 0L))
            is Action.Alarm -> clock(ctx, Intent("$CLOCK.ALARM").putExtra("hour", action.hour).putExtra("minute", action.minute))
            Action.AlarmCancel -> clock(ctx, Intent("$CLOCK.ALARM").putExtra("hour", -1))
        }
        tone(ToneGenerator.TONE_PROP_ACK)
    }

    /** Country searched first on radio-browser.info: Germany for German commands, else the locale's. */
    private fun radioCountry(): String? =
        if (parser.lang == "de") "DE" else Locale.getDefault().country.takeIf { it.length == 2 }

    private fun clock(ctx: Context, i: Intent) {
        ctx.sendBroadcast(i.setPackage(CLOCK))
    }

    private fun playRadio(ctx: Context, station: RadioBrowser.Station) {
        Log.i(Constants.TAG, "Radio: ${station.name} (${station.votes} votes) ${station.url}")
        RadioService.play(ctx, station)
        tone(ToneGenerator.TONE_PROP_ACK)
    }

    private fun spotify(ctx: Context, query: String) {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage(SPOTIFY)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            RadioService.stop(ctx)
            ctx.startActivity(i)
            // Spotify comes to the front to start playback; show the clock again afterwards.
            main.postDelayed({ VoiceModeA11yService.goHome() }, 6_000)
            tone(ToneGenerator.TONE_PROP_ACK)
        } catch (e: Exception) {
            Log.w(Constants.TAG, "Spotify search failed", e)
            tone(ToneGenerator.TONE_PROP_NACK)
        }
    }

    fun mediaKey(audio: AudioManager, code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /** Short audible cue ("I'm listening" / "done"). */
    fun tone(type: Int, ms: Int = 120) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            tg.startTone(type, ms)
            main.postDelayed({ tg.release() }, ms + 200L)
        } catch (e: Exception) {
            Log.w(Constants.TAG, "tone failed", e)
        }
    }
}
