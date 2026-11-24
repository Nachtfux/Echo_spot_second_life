package ai.pocket.heyclaude.command

/**
 * English commands as Vosk (vosk-model-small-en-us) writes them, plus digits for the
 * `COMMAND` test broadcast:
 *   music  – "play Queen (on Spotify)", "pause", "stop the music", "resume", "next", "go back",
 *            "louder", "volume down", "much quieter"
 *   radio  – "play radio SWR3", "radio BBC one", "turn on radio KEXP", "radio off"
 *   timer  – "set a timer for 5 minutes", "timer for one and a half hours", "half an hour",
 *            "cancel the timer"
 *   alarm  – "wake me up at 7", "set an alarm for 6:30", "alarm at seven thirty",
 *            "half past seven", "quarter to eight", "6 am", "cancel the alarm"
 */
object EnglishParser : CommandParser {
    override val lang = "en"

    /** Assistant name right after the wake word ("hey claude", "ok chat g p t"). */
    private val LEADING_NAME = Regex("^((hey|hi|ok|okay) )?(claude|clod|cloud|chat ?gpt|chat g p t)\\b")
    private val FILLER = Regex(
        "\\b(please|hey|okay|ok|claude|chat ?gpt|chat g p t|can you|could you|would you|will you" +
            "|i want to|i'd like to|i would like to|for me|just|on spotify|from spotify|in spotify|spotify)\\b",
    )

    private val TIMER_WORD = Regex("\\b(timers?|countdown)\\b")
    private val TIMER_CANCEL = Regex(
        "\\b(cancel|stop|delete|clear|remove|end|turn off|switch off|kill)\\b.*\\b(timers?|countdown)\\b" +
            "|\\b(timers?|countdown)\\b.*\\b(off|cancel|cancell?ed|stop|delete)\\b",
    )
    private val ALARM_WORD = Regex("\\b(alarms?|wake me|wake up|wake-up)\\b")
    private val ALARM_CANCEL = Regex(
        "\\b(cancel|stop|delete|clear|remove|turn off|switch off|disable)\\b.*\\b(alarms?|wake ?up call)\\b" +
            "|\\balarms?\\b.*\\b(off|cancel|cancell?ed|stop|delete)\\b|\\b(don't|do not) wake me\\b",
    )

    private val RADIO_STOP = Regex(
        "^((the )?radio (off|stop)|(stop|pause|end|turn off|switch off)( the)? radio|(turn|switch) (the )?radio off)$",
    )
    private val RADIO = Regex("^(?:play |turn on |switch on |start |put on |listen to )?(?:the )?(?:radio|station)(?: station)?\\s+(.+)$")

    private val PLAY_PREVIOUS = Regex("^play (the )?(previous|last)( song| track| one| title)?$")
    private val PLAY_NEXT = Regex("^play (the )?next( song| track| one| title)?$")
    private val PREVIOUS = Regex("\\b(previous|go back|skip back|last (song|track|one|title))\\b|^back$")
    private val NEXT = Regex("\\b(next|skip)\\b")
    private val MUCH = Regex("\\b(much|a lot|lot|way)\\b")
    private val LOUDER = Regex(
        "\\b(louder|volume up|turn (it |the volume |the music )?up|increase (the )?volume|raise (the )?volume)\\b",
    )
    private val QUIETER = Regex(
        "\\b(quieter|softer|less loud|volume down|turn (it |the volume |the music )?down" +
            "|decrease (the )?volume|lower (the )?volume)\\b",
    )
    private val PAUSE = Regex(
        "^(pause|stop|halt|silence|quiet|be quiet|stop it|stop playing|stop playback|(stop|pause) (the )?music" +
            "|pause it|pause playback|music (off|stop|pause))$",
    )
    private val RESUME = Regex(
        "^(resume|continue|play|unpause|go on|keep playing|continue playing|resume (the )?music|resume playback" +
            "|play (some )?music|music( on)?|start (the )?music)$",
    )
    private val PLAY = Regex(
        "^(?:play|put on|start playing|listen to)\\s+" +
            "(?:(?:some |a |the )?(?:music|songs?|album|playlist|track)\\s+|some\\s+)?(?:(?:by|from)\\s+)?(.*)$",
    )
    private val GENERIC_MUSIC = setOf("music", "songs", "something", "anything")

    private const val UNITS = "hours?|hrs?|minutes?|mins?|seconds?|secs?"
    private val UNIT_WORD = Regex("^($UNITS)$")
    private val DURATION_WORDS = setOf("a", "an", "and", "half", "quarter", "quarters", "of", "for", "in")

    override fun parse(raw: String): Action? {
        val t = normalize(raw)
        if (t.isEmpty()) return null

        // Timer & alarm
        if (TIMER_CANCEL.containsMatchIn(t)) return Action.TimerCancel
        if (ALARM_CANCEL.containsMatchIn(t)) return Action.AlarmCancel
        if (TIMER_WORD.containsMatchIn(t) || isBareDuration(t)) {
            parseDuration(t)?.let { return Action.Timer(it) }
        }
        if (ALARM_WORD.containsMatchIn(t)) {
            // "wake me up in twenty minutes" is a timer.
            if (Regex("\\bin\\b").containsMatchIn(t)) parseDuration(t)?.let { return Action.Timer(it) }
            parseClock(t)?.let { return Action.Alarm(it.first, it.second) }
        }

        // Radio
        if (RADIO_STOP.matches(t)) return Action.RadioStop
        RADIO.matchEntire(t)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty()) return Action.Radio(name)
        }

        // Music. "play …" is a search unless it is "play the next/previous song".
        val playRequest = t.startsWith("play ")
        when {
            PLAY_PREVIOUS.matches(t) -> return Action.Previous
            PLAY_NEXT.matches(t) -> return Action.Next
            !playRequest && PREVIOUS.containsMatchIn(t) -> return Action.Previous
            !playRequest && NEXT.containsMatchIn(t) -> return Action.Next
            !playRequest && LOUDER.containsMatchIn(t) -> return Action.Volume(if (MUCH.containsMatchIn(t)) 4 else 2)
            !playRequest && QUIETER.containsMatchIn(t) -> return Action.Volume(if (MUCH.containsMatchIn(t)) -4 else -2)
            PAUSE.matches(t) -> return Action.Pause
            RESUME.matches(t) -> return Action.Play
        }
        PLAY.matchEntire(t)?.let { m ->
            val q = m.groupValues[1].trim()
            return if (q.isEmpty() || q in GENERIC_MUSIC) Action.Play else Action.PlaySearch(q)
        }
        return null
    }

    override fun stationQuery(spoken: String): String = EnglishNumbers.digitsAndLetters(spoken)

    /** Lower case, "a.m." -> "am", "6pm" -> "6 pm", "6.30" -> "6:30", no punctuation, no filler words. */
    internal fun normalize(raw: String): String {
        var t = raw.lowercase()
        t = Regex("\\b([ap])\\.\\s?m\\.?").replace(t) { it.groupValues[1] + "m" }
        t = Regex("(\\d)([ap]m)\\b").replace(t) { it.groupValues[1] + " " + it.groupValues[2] }
        t = Regex("\\b(\\d{1,2})\\.(\\d{2})\\b").replace(t) { it.groupValues[1] + ":" + it.groupValues[2] }
        t = t.replace(Regex("[,.!?;\"]"), " ")
        t = Regex("\\b([ap]) m\\b").replace(t) { it.groupValues[1] + "m" }   // Vosk: "six p m"
        t = t.replace(Regex("\\s+"), " ").trim()
        t = LEADING_NAME.replace(t, " ")
        t = FILLER.replace(t, " ").replace(Regex("\\s+"), " ").trim()
        return EnglishNumbers.joinCompounds(t)
    }

    /** Only duration words ("ten minutes", "half an hour", "in an hour and a half"). */
    private fun isBareDuration(t: String): Boolean {
        val words = t.split(' ')
        return words.any { UNIT_WORD.matches(it) || it == "hour" } &&
            words.all { it in DURATION_WORDS || UNIT_WORD.matches(it) || EnglishNumbers.parse(it) != null }
    }

    /**
     * "five minutes", "an hour and a half", "one and a half hours", "half an hour",
     * "a quarter of an hour", "1 hour 30 minutes", "ninety seconds" -> seconds (max 24 h).
     */
    fun parseDuration(t: String): Long? {
        var rest = t
        var total = 0L
        fun take(re: Regex, value: (MatchResult) -> Long?) {
            rest = re.replace(rest) { m ->
                val v = value(m)
                if (v == null) m.value else {
                    total += v
                    " "
                }
            }
        }
        // "one and a half hours", "two and a half minutes"
        take(Regex("\\b(\\S+) and a half (hours?|minutes?)\\b")) { m ->
            amount(m.groupValues[1])?.let { it * unit(m.groupValues[2]) + unit(m.groupValues[2]) / 2 }
        }
        // "an hour and a half", "two hours and a half"
        take(Regex("\\b(\\S+) (hours?|minutes?) and a half\\b")) { m ->
            amount(m.groupValues[1])?.let { it * unit(m.groupValues[2]) + unit(m.groupValues[2]) / 2 }
        }
        take(Regex("\\bhalf (?:an |a )?(hour|minute)\\b")) { m -> unit(m.groupValues[1]) / 2 }
        take(Regex("\\b(three )?quarters? (?:of )?(?:an )?hour\\b")) { m -> if (m.groupValues[1].isEmpty()) 900L else 2700L }
        take(Regex("\\b(\\S+) ($UNITS)\\b")) { m -> amount(m.groupValues[1])?.let { it * unit(m.groupValues[2]) } }
        return total.takeIf { it in 1..(24 * 3600) }
    }

    private fun unit(u: String): Long = when {
        u.startsWith("h") -> 3600L
        u.startsWith("m") -> 60L
        else -> 1L
    }

    private fun amount(w: String): Long? = if (w == "a" || w == "an") 1L else EnglishNumbers.parse(w)?.toLong()

    /**
     * "7", "seven", "6:30", "seven thirty", "seven oh five", "half past seven", "quarter to eight",
     * "ten past six", "6 am", "6 pm", "noon" -> (hour, minute) in 24 h time.
     */
    fun parseClock(t: String): Pair<Int, Int>? {
        val pm = Regex("\\bpm\\b|\\bin the (afternoon|evening)\\b").containsMatchIn(t)
        val am = Regex("\\bam\\b|\\bin the morning\\b").containsMatchIn(t)
        fun n(w: String?) = w?.let { EnglishNumbers.parse(it) }
        fun hour(w: String?): Int? {
            val h = n(w)?.takeIf { it in 0..23 } ?: return null
            return when {
                pm && h in 1..11 -> h + 12
                am && h == 12 -> 0
                else -> h
            }
        }
        if (Regex("\\bnoon\\b").containsMatchIn(t)) return Pair(12, 0)
        if (Regex("\\bmidnight\\b").containsMatchIn(t)) return Pair(0, 0)
        Regex("\\bhalf past (\\S+)").find(t)?.let { m -> hour(m.groupValues[1])?.let { return Pair(it, 30) } }
        Regex("\\bquarter past (\\S+)").find(t)?.let { m -> hour(m.groupValues[1])?.let { return Pair(it, 15) } }
        Regex("\\bquarter to (\\S+)").find(t)?.let { m -> hour(m.groupValues[1])?.let { return Pair((it + 23) % 24, 45) } }
        for (m in Regex("\\b(\\S+) (?:minutes? )?(past|to) (\\S+)").findAll(t)) {
            val min = n(m.groupValues[1])?.takeIf { it in 1..30 } ?: continue
            val h = hour(m.groupValues[3]) ?: continue
            return if (m.groupValues[2] == "past") Pair(h, min) else Pair((h + 23) % 24, 60 - min)
        }
        Regex("\\b(\\d{1,2}):(\\d{2})\\b").find(t)?.let { m ->
            val h = hour(m.groupValues[1])
            val min = m.groupValues[2].toInt()
            if (h != null && min in 0..59) return Pair(h, min)
        }
        // First number that can be an hour, optionally followed by minutes ("thirty", "oh five").
        val words = t.split(' ')
        for ((i, w) in words.withIndex()) {
            val h = hour(w) ?: continue
            val next = words.getOrNull(i + 1)
            val min = if (next == "oh" || next == "o") {
                n(words.getOrNull(i + 2))?.takeIf { it in 0..9 } ?: 0
            } else {
                n(next)?.takeIf { it in 0..59 } ?: 0
            }
            return Pair(h, min)
        }
        return null
    }
}
