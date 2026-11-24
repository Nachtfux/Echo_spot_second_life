package ai.pocket.heyclaude.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Duration ("ten minutes") and clock-time ("half past seven") parsing in both languages. */
class DurationClockTest {

    @Test
    fun germanDuration() = assertTable(
        listOf(
            "zehn minuten" to 600L,
            "eine minute" to 60L,
            "eine stunde und fünf minuten" to 3900L,
            "halbe stunde" to 1800L,
            "viertelstunde" to 900L,
            "anderthalb stunden" to 5400L,
            "neunzig sekunden" to 90L,
            "90 sekunden" to 90L,
            "fünfundzwanzig minuten" to 1500L,
            "dreißig stunden" to null,
            "minuten" to null,
            "" to null,
        ),
        GermanParser::parseDuration,
    )

    @Test
    fun germanClock() = assertTable(
        listOf(
            "sieben uhr" to Pair(7, 0),
            "sieben uhr dreißig" to Pair(7, 30),
            "zwanzig uhr fünfzehn" to Pair(20, 15),
            "halb sieben" to Pair(6, 30),
            "halb eins" to Pair(0, 30),
            "viertel nach acht" to Pair(8, 15),
            "viertel vor acht" to Pair(7, 45),
            "wecker um sechs" to Pair(6, 0),
            "wecker für 7" to Pair(7, 0),
            "fünfundzwanzig uhr" to null,
            "wecker" to null,
        ),
        GermanParser::parseClock,
    )

    @Test
    fun englishDuration() = assertTable(
        listOf(
            "five minutes" to 300L,
            "a minute" to 60L,
            "an hour" to 3600L,
            "an hour and a half" to 5400L,
            "one and a half hours" to 5400L,
            "two and a half minutes" to 150L,
            "half an hour" to 1800L,
            "a quarter of an hour" to 900L,
            "three quarters of an hour" to 2700L,
            "1 hour 30 minutes" to 5400L,
            "an hour and five minutes" to 3900L,
            "ninety seconds" to 90L,
            "twenty-five minutes" to 1500L,
            "5 mins" to 300L,
            "thirty hours" to null,
            "minutes" to null,
            "" to null,
        ),
        EnglishParser::parseDuration,
    )

    @Test
    fun englishClock() = assertTable(
        listOf(
            "7" to Pair(7, 0),
            "at seven" to Pair(7, 0),
            "6:30" to Pair(6, 30),
            "seven thirty" to Pair(7, 30),
            "seven forty-five" to Pair(7, 45),
            "seven oh five" to Pair(7, 5),
            "seven o'clock" to Pair(7, 0),
            "half past seven" to Pair(7, 30),
            "quarter past eight" to Pair(8, 15),
            "quarter to eight" to Pair(7, 45),
            "ten past six" to Pair(6, 10),
            "twenty to seven" to Pair(6, 40),
            "6 am" to Pair(6, 0),
            "6 pm" to Pair(18, 0),
            "12 am" to Pair(0, 0),
            "12 pm" to Pair(12, 0),
            "half past seven pm" to Pair(19, 30),
            "quarter to eight pm" to Pair(19, 45),
            "seven in the evening" to Pair(19, 0),
            "noon" to Pair(12, 0),
            "midnight" to Pair(0, 0),
            "at 18:15" to Pair(18, 15),
            "no time here" to null,
        ),
        EnglishParser::parseClock,
    )

    @Test
    fun germanNumbers() {
        assertEquals(25, GermanNumbers.parse("fünfundzwanzig"))
        assertEquals(1, GermanNumbers.parse("eine"))
        assertEquals(100, GermanNumbers.parse("hundert"))
        assertNull(GermanNumbers.parse("stunde"))
        assertEquals("fünfundzwanzig minuten", GermanNumbers.joinCompounds("fünf und zwanzig minuten"))
        assertEquals("swr3", GermanNumbers.digitsAndLetters("s w r drei"))
        assertEquals("bayern 3", GermanNumbers.digitsAndLetters("bayern drei"))
        assertEquals("1 live", GermanNumbers.digitsAndLetters("eins live"))
    }

    @Test
    fun englishNumbers() {
        assertEquals(7, EnglishNumbers.parse("seven"))
        assertEquals(13, EnglishNumbers.parse("13"))
        assertEquals(25, EnglishNumbers.parse("twenty-five"))
        assertEquals(100, EnglishNumbers.parse("hundred"))
        assertNull(EnglishNumbers.parse("a"))
        assertNull(EnglishNumbers.parse("twenty-zero"))
        assertEquals("forty-five minutes", EnglishNumbers.joinCompounds("forty five minutes"))
        assertEquals("bbc radio 1", EnglishNumbers.digitsAndLetters("b b c radio one"))
        assertEquals("kexp", EnglishNumbers.digitsAndLetters("k e x p"))
        assertEquals("bbc radio 1", EnglishParser.stationQuery("BBC radio one"))
        assertEquals("swr3", GermanParser.stationQuery("s w r drei"))
    }
}
