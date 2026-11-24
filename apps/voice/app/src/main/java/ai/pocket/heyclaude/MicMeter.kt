package ai.pocket.heyclaude

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Lightweight voice-activity meter that runs next to Claude's voice mode, so EchoGuard can
 * tell "nobody is talking" apart from "the user is still speaking". Only levels are
 * computed; no audio is stored or sent anywhere.
 *
 * Android may hand a background app silence while another app records; [silenced] says so,
 * and EchoGuard then falls back to Claude's transcript as the activity signal.
 */
class MicMeter {
    @Volatile var lastVoiceAt = 0L
        private set
    @Volatile var silenced = false
        private set
    @Volatile var level = 0f
        private set

    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission") // RECORD_AUDIO is granted before the service runs
    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "heyclaude-micmeter").apply { start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        val rate = 16000
        val frame = ShortArray(320)                      // 20 ms
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 16000))
        } catch (e: Exception) {
            Log.w(Constants.TAG, "MicMeter: AudioRecord failed", e)
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(Constants.TAG, "MicMeter: AudioRecord not initialized")
            rec.release()
            return
        }
        var floor = 0.002f
        var voicedFrames = 0
        var zeroSince = -1L
        var lastLog = 0L
        try {
            rec.startRecording()
            while (running) {
                val n = rec.read(frame, 0, frame.size)
                if (n <= 0) continue
                var sumSq = 0.0
                var peak = 0
                for (i in 0 until n) {
                    val s = frame[i].toInt()
                    if (s > peak) peak = s else if (-s > peak) peak = -s
                    sumSq += (s * s).toDouble()
                }
                val rms = (sqrt(sumSq / n) / 32768.0).toFloat()
                level = rms
                val now = SystemClock.uptimeMillis()

                if (peak == 0) {
                    if (zeroSince < 0) zeroSince = now
                    if (!silenced && now - zeroSince > 2000) {
                        silenced = true
                        Log.i(Constants.TAG, "MicMeter: capture is silenced by the OS")
                    }
                    continue
                }
                zeroSince = -1
                if (silenced) {
                    silenced = false
                    Log.i(Constants.TAG, "MicMeter: capture delivers audio again")
                }

                // Slowly tracking noise floor; speech = clearly above it for >= 60 ms.
                floor = if (rms < floor) rms else floor + (rms - floor) * 0.002f
                if (rms > max(floor * 4f, 0.004f)) {
                    if (++voicedFrames >= 3) lastVoiceAt = now
                } else {
                    voicedFrames = 0
                }
                if (WakeWordService.levelLog && now - lastLog > 500) {
                    lastLog = now
                    Log.d(Constants.TAG, "MicMeter: rms=%.4f floor=%.4f peak=%d voice=%s".format(
                        rms, floor, peak, now - lastVoiceAt < 300))
                }
            }
        } catch (e: Exception) {
            Log.w(Constants.TAG, "MicMeter: loop failed", e)
        } finally {
            try {
                rec.stop()
            } catch (_: Exception) {
            }
            rec.release()
        }
    }
}
