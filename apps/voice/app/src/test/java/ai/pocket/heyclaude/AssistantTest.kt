package ai.pocket.heyclaude

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Accessibility labels and spoken stop phrases of the assistant profiles. */
class AssistantTest {

    private fun assertAll(what: String, labels: List<String>, expected: Boolean, check: (String) -> Boolean) {
        val wrong = labels.filter { check(it) != expected }
        assertTrue("$what should be $expected for: $wrong", wrong.isEmpty())
    }

    @Test
    fun claudeValuesAreTheDeviceTestedOnes() {
        // Calibrated on a real Echo Spot; change only after re-testing with DUMP_NODES.
        val c = Assistant.CLAUDE
        assertEquals("(?i)voice|sprach|stimm|sound ?wave|audio", c.voiceButton.pattern)
        assertEquals(
            "(?i)eingabe|diktier|dictat|voice input|speech.to.text|transcri" +
                "|antwort|response|reply|vorlesen|read aloud|stimme stummschalten|mute claude",
            c.voiceButtonExclude.pattern,
        )
        assertEquals("(?i)new chat|neuer chat|start new", c.newChatButton.pattern)
        assertEquals("(?i)^(mikrofon stummschalten|mute microphone|mute mic|mute)$", c.micMuteLabel.pattern)
        assertEquals("(?i)stummschaltung aufheben|^unmute", c.micUnmuteLabel.pattern)
        assertEquals("(?i)^(beenden|end|end call|end voice mode)$", c.endButton.pattern)
        assertEquals("(?i)^(beenden|auflegen|end|end call|hang up)$", c.endNotificationAction.pattern)
        assertEquals("com.anthropic.claude", c.packageName)
    }

    @Test
    fun claudeVoiceButton() {
        val c = Assistant.CLAUDE
        assertAll("voice button", listOf("Sprachmodus", "Sprachmodus starten", "Voice mode", "Start voice mode"), true) {
            c.isVoiceButtonLabel(it)
        }
        assertAll(
            "voice button",
            listOf("Spracheingabe starten", "Claude, Sprachantwort", "Diktieren", "Vorlesen", "Claudes Stimme stummschalten"),
            false,
        ) { c.isVoiceButtonLabel(it) }
    }

    @Test
    fun claudeVoiceModeButtons() {
        val c = Assistant.CLAUDE
        assertTrue(c.isMicMuteLabel("Mikrofon stummschalten"))
        assertTrue(c.isMicMuteLabel("Mute microphone"))
        assertFalse(c.isMicMuteLabel("Mikrofon-Stummschaltung aufheben"))
        assertFalse(c.isMicMuteLabel("Claudes Stimme stummschalten"))
        assertTrue(c.isMicUnmuteLabel("Mikrofon-Stummschaltung aufheben"))
        assertTrue(c.isMicUnmuteLabel("Unmute"))
        assertFalse(c.isMicUnmuteLabel("Mikrofon stummschalten"))
        assertTrue(c.isEndButtonLabel("Beenden"))
        assertTrue(c.isEndButtonLabel("End voice mode"))
        assertFalse(c.isEndButtonLabel("Neuer Chat"))
        assertTrue(c.isEndNotificationAction("Beenden"))
        assertTrue(c.isEndNotificationAction("Auflegen"))
        assertFalse(c.isEndNotificationAction("Öffnen"))
    }

    @Test
    fun claudeStopPhrases() {
        val c = Assistant.CLAUDE
        assertAll(
            "stop phrase",
            listOf(
                "Danke Claude", "danke, Claude!", "Dankeschön Claude", "Claude stopp", "Tschüss Claude",
                "Auf Wiedersehen, Claude.", "Danke Klaud", "thank you Claude", "Thank you very much, Claude",
                "Thanks, Claude.", "stop Claude", "bye Claude", "Goodbye Claude", "Claude, that's all",
            ),
            true,
        ) { c.isStopPhrase(it) }
        assertAll(
            "stop phrase",
            listOf("Claude, wie wird das Wetter?", "Thank you for the answer", "I love Claude Monet", "Danke ChatGPT"),
            false,
        ) { c.isStopPhrase(it) }
    }

    @Test
    fun chatGptLabels() {
        val g = Assistant.CHATGPT
        assertEquals("com.openai.chatgpt", g.packageName)
        assertAll(
            "voice button",
            listOf("Sprachmodus starten", "Start voice mode", "Voice mode", "Sprachmodus", "voice_mode_button"),
            true,
        ) { g.isVoiceButtonLabel(it) }
        assertAll(
            "voice button",
            listOf("Diktieren", "Dictate", "Spracheingabe", "Transcribe", "End voice mode", "Sprachmodus beenden", "Vorlesen"),
            false,
        ) { g.isVoiceButtonLabel(it) }
        assertAll("mute", listOf("Mikrofon stummschalten", "Mute microphone", "Mute"), true) { g.isMicMuteLabel(it) }
        assertAll("mute", listOf("Stummschaltung aufheben", "Unmute"), false) { g.isMicMuteLabel(it) }
        assertAll("unmute", listOf("Stummschaltung aufheben", "Unmute"), true) { g.isMicUnmuteLabel(it) }
        assertAll(
            "end",
            listOf("Beenden", "End", "Sprachmodus beenden", "End voice mode", "Schließen", "Close"),
            true,
        ) { g.isEndButtonLabel(it) }
        assertFalse(g.isEndButtonLabel("Sprachmodus starten"))
    }

    @Test
    fun chatGptStopPhrases() {
        val g = Assistant.CHATGPT
        assertAll(
            "stop phrase",
            listOf("thanks ChatGPT", "Thank you, ChatGPT!", "stop ChatGPT", "bye ChatGPT", "Danke ChatGPT", "ChatGPT stopp", "thank you chat gpt"),
            true,
        ) { g.isStopPhrase(it) }
        assertAll("stop phrase", listOf("thanks Claude", "ChatGPT, what time is it?"), false) { g.isStopPhrase(it) }
    }

    @Test
    fun idsAndModelFiles() {
        assertEquals(Assistant.CLAUDE, Assistant.fromId("claude"))
        assertEquals(Assistant.CHATGPT, Assistant.fromId("ChatGPT"))
        assertEquals(Assistant.CHATGPT, Assistant.fromId(" chatgpt "))
        assertNull(Assistant.fromId("gemini"))
        assertNull(Assistant.fromId(null))
        assertEquals(Assistant.CLAUDE, Assistant.DEFAULT)
        assertEquals("hey_claude.onnx", Assistant.CLAUDE.wakeModelFile)
        assertEquals("danke_claude.onnx", Assistant.CLAUDE.stopModelFile)
        assertEquals("hey_chatgpt.onnx", Assistant.CHATGPT.wakeModelFile)
        assertEquals("danke_chatgpt.onnx", Assistant.CHATGPT.stopModelFile)
        assertEquals("Claude", Assistant.CLAUDE.displayName)
        assertEquals("ChatGPT", Assistant.CHATGPT.displayName)
    }
}
