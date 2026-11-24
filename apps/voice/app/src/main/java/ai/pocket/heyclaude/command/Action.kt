package ai.pocket.heyclaude.command

/** What an offline voice command asks for; produced by a [CommandParser], run by [VoiceCommands]. */
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
