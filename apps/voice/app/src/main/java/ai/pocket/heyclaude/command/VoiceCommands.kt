package ai.pocket.heyclaude.command

import ai.pocket.heyclaude.Constants
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

/**
 * Short commands right after the wake word, recognised OFFLINE on the Spot with Vosk
 * (vosk-model-small-de, pushed via adb to getExternalFilesDir(null)/[MODEL_DIR]):
 *   music  – "spiel Queen", "Pause", "weiter", "nächstes Lied", "lauter"
 *   radio  – "spiel SWR3", "Radio Bayern drei", "Radio aus"
 *   timer  – "Timer zehn Minuten", "Timer aus"; alarm – "Wecker um halb sieben", "Wecker aus"
 * Anything else goes to Claude. Timers/alarms live in the Spot clock app (broadcasts below).
 */
object VoiceCommands {
    const val MODEL_DIR = "vosk-de"
    private const val SPOTIFY = "com.spotify.music"
    private const val CLOCK = "ai.pocket.spotclock"
    private const val SAMPLE_RATE = 16000f

    @Volatile private var model: Model? = null
    @Volatile private var loading = false
    private val main = Handler(Looper.getMainLooper())

    sealed class Action {
        data class PlaySearch(val query: String) : Action()
        data class Radio(val station: String) : Action()
        object RadioStop : Action()
        object Play : Action()
        object Pause : Action()
        object Next : Action()
        object Previous : Action()
        data class Volume(val steps: Int) : Action()
        data class Timer(val seconds: Long) : Action()
        object TimerCancel : Action()
        data class Alarm(val hour: Int, val minute: Int) : Action()
        object AlarmCancel : Action()

        override fun toString(): String = when (this) {
            is PlaySearch -> "PlaySearch($query)"
            is Radio -> "Radio($station)"
            is Volume -> "Volume($steps)"
            is Timer -> "Timer(${seconds}s)"
            is Alarm -> "Alarm(%02d:%02d)".format(hour, minute)
            else -> javaClass.simpleName
        }
    }

    /** Loads the Vosk model in the background (a few seconds on the Spot). */
    fun preload(ctx: Context) {
        if (model != null || loading) return
        val dir = File(ctx.getExternalFilesDir(null), MODEL_DIR)
        if (!File(dir, "am").isDirectory) {
            Log.i(Constants.TAG, "Voice commands off: no Vosk model in $dir")
            return
        }
        loading = true
        Thread({
            try {
                val t0 = SystemClock.elapsedRealtime()
                model = Model(dir.absolutePath)
                Log.i(Constants.TAG, "Vosk model ready in ${SystemClock.elapsedRealtime() - t0} ms")
            } catch (e: Throwable) {
                Log.e(Constants.TAG, "Vosk model failed to load", e)
            } finally {
                loading = false
            }
        }, "vosk-load").start()
    }

    val ready: Boolean get() = model != null

    /**
     * Listens for one short utterance after the wake word. [read] fills the frame and returns
     * false on mic errors. Returns "" if nothing was said (a plain "Hey Claude").
     */
    fun recognize(frame: ShortArray, read: (ShortArray) -> Boolean, stop: () -> Boolean): String {
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

    private fun textOf(json: String, key: String): String =
        try {
            JSONObject(json).optString(key, "").trim()
        } catch (_: Exception) {
            ""
        }

    // ------------------------------------------------------------- parsing --

    private val FILLER = Regex("\\b(bitte|mal|doch|hey|claude|klaud|cloud|auf spotify|bei spotify|von spotify|spotify)\\b")
    private val PLAY = Regex("^(spiel|spiele|spielt|spielen|play|leg|lege)\\b\\s*(auf|ab)?\\s*(musik|lieder|songs|was|etwas|das lied|den song|das album|die playlist)?\\s*(von)?\\s*(.*)$")
    private val TIMER_WORD = "(timer|teimer|taimer|countdown|stoppuhr|kurzzeitwecker)"

    fun parse(raw: String): Action? {
        val t = GermanNumbers.joinCompounds(FILLER.replace(raw.lowercase(), " ").replace(Regex("\\s+"), " ").trim())
        if (t.isEmpty()) return null

        // Timer & alarm
        if (Regex("\\b$TIMER_WORD\\b.*\\b(aus|stopp?|abbrechen|löschen|beenden)\\b|\\b(lösch|stopp?)\\w* (den )?$TIMER_WORD").containsMatchIn(t)) return Action.TimerCancel
        if (Regex("\\bwecker\\b.*\\b(aus|stopp?|abbrechen|löschen|ausschalten)\\b|\\blösch\\w* (den )?wecker").containsMatchIn(t)) return Action.AlarmCancel
        if (Regex("\\b$TIMER_WORD\\b").containsMatchIn(t) || Regex("^(in )?\\S+ (stunden?|minuten?|sekunden?)$").matches(t)) {
            parseDuration(t)?.let { return Action.Timer(it) }
        }
        if (Regex("\\b(wecker|weck mich|wecke mich|weckruf)\\b").containsMatchIn(t)) {
            parseClock(t)?.let { return Action.Alarm(it.first, it.second) }
        }

        // Radio
        if (Regex("^(das )?radio (aus|stopp?)$").matches(t)) return Action.RadioStop
        Regex("^(spiel |spiele |mach |schalt |schalte |starte? )?(das |den )?(radio|sender)( an)?\\s+(.+)$").matchEntire(t)?.let {
            val name = it.groupValues[5].trim()
            if (name.isNotEmpty()) return Action.Radio(name)
        }

        // Music
        when {
            Regex("\\b(nächste|nächstes|nächster|skip|überspring\\w*)\\b").containsMatchIn(t) -> return Action.Next
            Regex("\\b(vorherige\\w*|zurück|letzte lied|letzten song|nochmal)\\b").containsMatchIn(t) -> return Action.Previous
            Regex("\\b(viel )?lauter\\b").containsMatchIn(t) -> return Action.Volume(if ("viel" in t) 4 else 2)
            Regex("\\b(viel )?leiser\\b").containsMatchIn(t) -> return Action.Volume(if ("viel" in t) -4 else -2)
            Regex("^(pause|anhalten|halt|stopp?|ruhe|aus|musik (aus|stopp?|pause))$").matches(t) -> return Action.Pause
            Regex("^(weiter|weiterspielen|fortsetzen|play|musik( an| starten| weiter)?)$").matches(t) -> return Action.Play
        }
        PLAY.matchEntire(t)?.let { m ->
            val q = m.groupValues[5].trim()
            return if (q.isEmpty() || q == "musik" || q == "weiter") Action.Play else Action.PlaySearch(q)
        }
        return null
    }

    /** "zehn minuten", "eine stunde und fünf minuten", "halbe stunde", "90 sekunden" -> seconds. */
    fun parseDuration(t: String): Long? {
        var total = 0L
        if (Regex("\\bhalbe(n)? stunde\\b").containsMatchIn(t)) total += 1800
        if (Regex("\\bviertel ?stunde\\b").containsMatchIn(t)) total += 900
        if (Regex("\\banderthalb stunden?\\b").containsMatchIn(t)) total += 5400
        for (m in Regex("(\\S+) (stunden?|minuten?|sekunden?)\\b").findAll(t)) {
            val n = GermanNumbers.parse(m.groupValues[1]) ?: continue
            total += n.toLong() * when {
                m.groupValues[2].startsWith("stunde") -> 3600
                m.groupValues[2].startsWith("minute") -> 60
                else -> 1
            }
        }
        return total.takeIf { it in 1..(24 * 3600) }
    }

    /** "sieben uhr", "sieben uhr dreißig", "halb sieben", "viertel nach acht", "auf sechs" -> (h, m). */
    fun parseClock(t: String): Pair<Int, Int>? {
        fun n(w: String) = GermanNumbers.parse(w)
        Regex("\\bhalb (\\S+)").find(t)?.let { m -> n(m.groupValues[1])?.let { return Pair((it + 23) % 24, 30) } }
        Regex("\\bviertel nach (\\S+)").find(t)?.let { m -> n(m.groupValues[1])?.let { return Pair(it % 24, 15) } }
        Regex("\\bviertel vor (\\S+)").find(t)?.let { m -> n(m.groupValues[1])?.let { return Pair((it + 23) % 24, 45) } }
        Regex("\\b(\\S+) uhr(?: (\\S+))?").find(t)?.let { m ->
            val h = n(m.groupValues[1]) ?: return null
            val min = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { n(it) } ?: 0
            if (h in 0..23 && min in 0..59) return Pair(h, min)
        }
        Regex("\\b(auf|um|für) (\\S+)$").find(t)?.let { m -> n(m.groupValues[2])?.takeIf { it in 0..23 }?.let { return Pair(it, 0) } }
        return null
    }

    // ----------------------------------------------------------- executing --

    /** Runs on the main thread. */
    fun execute(ctx: Context, action: Action) {
        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (action) {
            is Action.PlaySearch -> {
                // A station with exactly this name ("SWR3") -> radio, else Spotify.
                Thread({
                    val station = RadioBrowser.find(action.query, strict = true)
                    main.post {
                        if (station != null) playRadio(ctx, station) else spotify(ctx, action.query)
                    }
                }, "radio-lookup").start()
                return
            }
            is Action.Radio -> {
                Thread({
                    val station = RadioBrowser.find(action.station, strict = false)
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
