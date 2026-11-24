package ai.pocket.heyclaude.command

import ai.pocket.heyclaude.command.Action.Alarm
import ai.pocket.heyclaude.command.Action.AlarmCancel
import ai.pocket.heyclaude.command.Action.Next
import ai.pocket.heyclaude.command.Action.Pause
import ai.pocket.heyclaude.command.Action.Play
import ai.pocket.heyclaude.command.Action.PlaySearch
import ai.pocket.heyclaude.command.Action.Previous
import ai.pocket.heyclaude.command.Action.Radio
import ai.pocket.heyclaude.command.Action.RadioStop
import ai.pocket.heyclaude.command.Action.Timer
import ai.pocket.heyclaude.command.Action.TimerCancel
import ai.pocket.heyclaude.command.Action.Volume
import org.junit.Assert.assertEquals
import org.junit.Test

/** English command table: every phrase of the documented grammar plus a few variants. */
class EnglishParserTest {

    @Test
    fun music() = assertTable(
        listOf(
            "play queen" to PlaySearch("queen"),
            "play queen on spotify" to PlaySearch("queen"),
            "Hey Claude, play Queen on Spotify." to PlaySearch("queen"),
            "chatgpt play the beatles" to PlaySearch("the beatles"),
            "can you play songs by adele" to PlaySearch("adele"),
            "play some jazz" to PlaySearch("jazz"),
            "play the album thriller" to PlaySearch("thriller"),
            "pause" to Pause,
            "stop" to Pause,
            "stop the music" to Pause,
            "resume" to Play,
            "continue" to Play,
            "play" to Play,
            "play some music" to Play,
            "next" to Next,
            "skip" to Next,
            "next song please" to Next,
            "skip this song" to Next,
            "play the next song" to Next,
            "previous" to Previous,
            "go back" to Previous,
            "previous song" to Previous,
            "louder" to Volume(2),
            "volume up" to Volume(2),
            "turn it up" to Volume(2),
            "much louder" to Volume(4),
            "quieter" to Volume(-2),
            "volume down" to Volume(-2),
            "turn it down" to Volume(-2),
            "much quieter" to Volume(-4),
        ),
        EnglishParser::parse,
    )

    @Test
    fun radio() = assertTable(
        listOf(
            "play radio bbc one" to Radio("bbc one"),
            "radio swr three" to Radio("swr three"),
            "turn on radio antenne bayern" to Radio("antenne bayern"),
            "turn on the radio station kexp" to Radio("kexp"),
            "radio off" to RadioStop,
            "turn off the radio" to RadioStop,
            "stop the radio" to RadioStop,
        ),
        EnglishParser::parse,
    )

    @Test
    fun timer() = assertTable(
        listOf(
            "set a timer for 5 minutes" to Timer(300),
            "set a timer for five minutes" to Timer(300),
            "timer 10 minutes" to Timer(600),
            "timer ten minutes" to Timer(600),
            "timer for one and a half hours" to Timer(5400),
            "set a timer for an hour and a half" to Timer(5400),
            "set a timer for half an hour" to Timer(1800),
            "half an hour" to Timer(1800),
            "timer twenty five minutes" to Timer(1500),
            "ten minutes" to Timer(600),
            "timer for 90 seconds" to Timer(90),
            "wake me up in twenty minutes" to Timer(1200),
            "cancel the timer" to TimerCancel,
            "stop the timer" to TimerCancel,
            "timer off" to TimerCancel,
        ),
        EnglishParser::parse,
    )

    @Test
    fun alarm() = assertTable(
        listOf(
            "wake me up at 7" to Alarm(7, 0),
            "wake me up at seven" to Alarm(7, 0),
            "set an alarm for 6:30" to Alarm(6, 30),
            "alarm at seven thirty" to Alarm(7, 30),
            "set an alarm for half past seven" to Alarm(7, 30),
            "wake me at quarter to eight" to Alarm(7, 45),
            "alarm at 6 am" to Alarm(6, 0),
            "alarm at 6 pm" to Alarm(18, 0),
            "set an alarm for six thirty p m" to Alarm(18, 30),
            "wake me up at 6 a.m." to Alarm(6, 0),
            "set an alarm for 7pm" to Alarm(19, 0),
            "wake me up at seven in the morning" to Alarm(7, 0),
            "cancel the alarm" to AlarmCancel,
            "turn off the alarm" to AlarmCancel,
            "alarm off" to AlarmCancel,
        ),
        EnglishParser::parse,
    )

    @Test
    fun noCommand() = assertTable(
        listOf<Pair<String, Action?>>(
            "" to null,
            "please" to null,
            "hey claude" to null,
            "ok chat g p t" to null,
            "timer" to null,
            "alarm" to null,
            "what's the weather like" to null,
            "tell me a joke" to null,
        ),
        EnglishParser::parse,
    )

    @Test
    fun normalize() {
        assertEquals("set an alarm for 6:30 am", EnglishParser.normalize("Hey Claude, set an alarm for 6.30 a.m.!"))
        assertEquals("timer twenty-five minutes", EnglishParser.normalize("OK ChatGPT timer twenty five minutes please"))
    }

    @Test
    fun parserForLanguage() {
        assertEquals(GermanParser, CommandParser.forLang("de"))
        assertEquals(EnglishParser, CommandParser.forLang("en"))
        assertEquals(listOf("de", "en"), CommandParser.LANGS)
    }
}
