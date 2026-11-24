package ai.pocket.heyclaude.command

/** English number words as Vosk (vosk-model-small-en-us) writes them ("ten", "twenty five"). */
object EnglishNumbers {
    private val UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
    )
    private val TEENS = mapOf(
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60,
        "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

    private val COMPOUND = Regex(
        "\\b(twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety)[ -](one|two|three|four|five|six|seven|eight|nine)\\b",
    )

    /** "twenty five" -> "twenty-five", so every number is a single token. */
    fun joinCompounds(text: String): String =
        COMPOUND.replace(text) { it.groupValues[1] + "-" + it.groupValues[2] }

    /** "7", "seven", "twenty-five", "hundred" -> Int; anything else -> null. */
    fun parse(word: String): Int? {
        val w = word.lowercase().trim()
        w.toIntOrNull()?.let { return it }
        UNITS[w]?.let { return it }
        TEENS[w]?.let { return it }
        TENS[w]?.let { return it }
        if (w == "hundred") return 100
        val dash = w.indexOf('-')
        if (dash > 0) {
            val t = TENS[w.substring(0, dash)]
            val u = UNITS[w.substring(dash + 1)]
            if (t != null && u != null && u > 0) return t + u
        }
        return null
    }

    /** "b b c radio one" -> "bbc radio 1", "k e x p" -> "kexp". */
    fun digitsAndLetters(text: String): String {
        val words = joinCompounds(text.lowercase()).split(Regex("\\s+")).filter { it.isNotEmpty() }
            .map { w -> parse(w)?.toString() ?: w }
        return squashSpelled(words)
    }
}
