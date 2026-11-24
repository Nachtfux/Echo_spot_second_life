package ai.pocket.heyclaude.command

/**
 * German commands as Vosk (vosk-model-small-de) writes them:
 *   music  – "spiel Queen", "Pause", "weiter", "nächstes Lied", "lauter"
 *   radio  – "spiel SWR3", "Radio Bayern drei", "Radio aus"
 *   timer  – "Timer zehn Minuten", "Timer aus"; alarm – "Wecker um halb sieben", "Wecker aus"
 */
object GermanParser : CommandParser {
    override val lang = "de"

    private val FILLER = Regex("\\b(bitte|mal|doch|hey|claude|klaud|cloud|chat ?gpt|auf spotify|bei spotify|von spotify|spotify)\\b")
    private val PLAY = Regex("^(spiel|spiele|spielt|spielen|play|leg|lege)\\b\\s*(auf|ab)?\\s*(musik|lieder|songs|was|etwas|das lied|den song|das album|die playlist)?\\s*(von)?\\s*(.*)$")
    private const val TIMER_WORD = "(timer|teimer|taimer|countdown|stoppuhr|kurzzeitwecker)"

    override fun parse(raw: String): Action? {
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

    override fun stationQuery(spoken: String): String = GermanNumbers.digitsAndLetters(spoken)

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
}
