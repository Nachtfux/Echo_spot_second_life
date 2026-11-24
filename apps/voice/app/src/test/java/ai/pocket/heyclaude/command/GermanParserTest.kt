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
import org.junit.Test

/** German command table (behaviour of the parser that runs on the device today). */
class GermanParserTest {

    @Test
    fun music() = assertTable(
        listOf(
            "spiel queen" to PlaySearch("queen"),
            "Spiel Queen" to PlaySearch("queen"),
            "hey claude spiel queen auf spotify" to PlaySearch("queen"),
            "hey chatgpt spiel queen" to PlaySearch("queen"),
            "spiel bitte die beatles" to PlaySearch("die beatles"),
            "spiele musik von queen" to PlaySearch("queen"),
            "spiel das album thriller" to PlaySearch("thriller"),
            "spiel musik" to Play,
            "spiel weiter" to Play,
            "musik" to Play,
            "weiter" to Play,
            "fortsetzen" to Play,
            "musik an" to Play,
            "pause" to Pause,
            "stopp" to Pause,
            "stop" to Pause,
            "anhalten" to Pause,
            "musik aus" to Pause,
            "aus" to Pause,
            "nächstes lied" to Next,
            "nächster song" to Next,
            "überspringen" to Next,
            "skip" to Next,
            "vorheriges lied" to Previous,
            "zurück" to Previous,
            "nochmal" to Previous,
            "lauter" to Volume(2),
            "bitte lauter" to Volume(2),
            "viel lauter" to Volume(4),
            "leiser" to Volume(-2),
            "viel leiser" to Volume(-4),
        ),
        GermanParser::parse,
    )

    @Test
    fun radio() = assertTable(
        listOf(
            "radio aus" to RadioStop,
            "das radio stopp" to RadioStop,
            "radio swr drei" to Radio("swr drei"),
            "spiel radio bayern drei" to Radio("bayern drei"),
            "schalte das radio an antenne bayern" to Radio("antenne bayern"),
            "sender eins live" to Radio("eins live"),
        ),
        GermanParser::parse,
    )

    @Test
    fun timerAndAlarm() = assertTable(
        listOf(
            "timer zehn minuten" to Timer(600),
            "stell einen timer auf fünf minuten" to Timer(300),
            "timer eine stunde und fünf minuten" to Timer(3900),
            "timer halbe stunde" to Timer(1800),
            "timer anderthalb stunden" to Timer(5400),
            "timer fünf und zwanzig minuten" to Timer(1500),
            "timer neunzig sekunden" to Timer(90),
            "zehn minuten" to Timer(600),
            "in zwei stunden" to Timer(7200),
            "timer aus" to TimerCancel,
            "timer abbrechen" to TimerCancel,
            "lösche den timer" to TimerCancel,
            "stoppe den timer" to TimerCancel,
            "wecker um sieben uhr" to Alarm(7, 0),
            "wecker um sieben uhr dreißig" to Alarm(7, 30),
            "weck mich um halb sieben" to Alarm(6, 30),
            "wecker viertel nach acht" to Alarm(8, 15),
            "wecker viertel vor acht" to Alarm(7, 45),
            "wecker auf sechs" to Alarm(6, 0),
            "wecker um 7 uhr" to Alarm(7, 0),
            "wecker aus" to AlarmCancel,
            "wecker ausschalten" to AlarmCancel,
            "lösche den wecker" to AlarmCancel,
        ),
        GermanParser::parse,
    )

    @Test
    fun noCommand() = assertTable(
        listOf<Pair<String, Action?>>(
            "" to null,
            "bitte" to null,
            "hey claude" to null,
            "timer" to null,
            "wecker" to null,
            "wie wird das wetter morgen" to null,
            "erzähl mir einen witz" to null,
        ),
        GermanParser::parse,
    )
}
