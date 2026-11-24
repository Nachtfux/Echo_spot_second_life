package ai.pocket.heyclaude.command

/**
 * Turns the text Vosk heard right after the wake word into an [Action], or null when it is
 * no command (then the assistant app opens). One implementation per command language.
 * Implementations are pure Kotlin objects, so they are covered by JVM unit tests.
 */
interface CommandParser {
    /** Language code, also the suffix of the Vosk model dir ("vosk-de", "vosk-en"). */
    val lang: String

    fun parse(raw: String): Action?

    /** Spoken station name -> radio-browser.info search text ("s w r drei" -> "swr3"). */
    fun stationQuery(spoken: String): String

    companion object {
        /** Supported command languages. */
        val LANGS = listOf("de", "en")

        fun forLang(lang: String): CommandParser = when (lang) {
            "en" -> EnglishParser
            else -> GermanParser
        }
    }
}

/**
 * Joins runs of single letters and digits into one token: ["s", "w", "r", "3"] -> "swr3",
 * ["bayern", "3"] -> "bayern 3". Used to turn spelled station names into searchable text.
 */
internal fun squashSpelled(words: List<String>): String {
    val out = StringBuilder()
    var prevShort = false
    for (w in words) {
        val short = w.length == 1 || w.all { it.isDigit() }
        if (out.isNotEmpty() && !(short && prevShort)) out.append(' ')
        out.append(w)
        prevShort = short
    }
    return out.toString()
}
