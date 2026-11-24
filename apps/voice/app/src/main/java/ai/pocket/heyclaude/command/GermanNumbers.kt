package ai.pocket.heyclaude.command

/** German number words as Vosk writes them ("zehn", "fünfundzwanzig", "fünf und zwanzig"). */
object GermanNumbers {
    private val UNITS = mapOf(
        "null" to 0, "ein" to 1, "eins" to 1, "eine" to 1, "einen" to 1, "einer" to 1, "zwei" to 2,
        "drei" to 3, "vier" to 4, "fünf" to 5, "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9,
    )
    private val TEENS = mapOf(
        "zehn" to 10, "elf" to 11, "zwölf" to 12, "dreizehn" to 13, "vierzehn" to 14, "fünfzehn" to 15,
        "sechzehn" to 16, "siebzehn" to 17, "achtzehn" to 18, "neunzehn" to 19,
    )
    private val TENS = mapOf(
        "zwanzig" to 20, "dreißig" to 30, "vierzig" to 40, "fünfzig" to 50, "sechzig" to 60,
        "siebzig" to 70, "achtzig" to 80, "neunzig" to 90,
    )

    /** "fünf und zwanzig" -> "fünfundzwanzig", so every number is a single token. */
    fun joinCompounds(text: String): String =
        Regex("\\b(ein|zwei|drei|vier|fünf|sechs|sieben|acht|neun) und (zwanzig|dreißig|vierzig|fünfzig|sechzig|siebzig|achtzig|neunzig)\\b")
            .replace(text) { it.groupValues[1] + "und" + it.groupValues[2] }

    fun parse(word: String): Int? {
        val w = word.lowercase().trim()
        w.toIntOrNull()?.let { return it }
        UNITS[w]?.let { return it }
        TEENS[w]?.let { return it }
        TENS[w]?.let { return it }
        if (w == "hundert" || w == "einhundert") return 100
        val i = w.indexOf("und")
        if (i > 0) {
            val u = UNITS[w.substring(0, i)]
            val t = TENS[w.substring(i + 3)]
            if (u != null && t != null) return u + t
        }
        return null
    }

    /** "s w r drei" -> "swr3", "bayern drei" -> "bayern 3", "eins live" -> "1 live". */
    fun digitsAndLetters(text: String): String {
        val words = joinCompounds(text.lowercase()).split(Regex("\\s+")).filter { it.isNotEmpty() }
            .map { w -> parse(w)?.toString() ?: w }
        return squashSpelled(words)
    }
}
