package ai.pocket.heyclaude.wake

import kotlin.math.max
import kotlin.math.min

/**
 * Saves CPU on the Spot: while the room is quiet the neural networks are skipped. Only the
 * frame level (RMS) is computed, which costs next to nothing. When sound starts, the last
 * [preRollFrames] frames are replayed so the detector is warm and the start of the wake word
 * is not lost.
 *
 * The noise floor snaps down to quiet frames and creeps up slowly, but is capped: in a loud
 * room (TV, music) the gate simply stays open instead of risking a missed wake word.
 */
class SilenceGate(private val frameSamples: Int, private val preRollFrames: Int = 16) {
    companion object {
        /** Frames with RMS above this (and above 3x the noise floor) count as sound. */
        const val MIN_LEVEL = 0.0008f
        private const val MAX_FLOOR = 0.002f
        private const val FLOOR_CREEP = 1.0005f
        /** Stay open this many frames (80 ms each) after the last sound: ~3 s. */
        private const val HOLD_FRAMES = 38
    }

    class Result(val frames: List<ShortArray>, val wokeUp: Boolean)

    private val ring = Array(preRollFrames) { ShortArray(frameSamples) }
    private var ringHead = 0
    private var ringCount = 0
    private var floor = 0.0005f
    private var frame = 0L
    private var lastLoudAt = -1L

    var active = false
        private set
    var processedFrames = 0L
        private set
    var totalFrames = 0L
        private set

    fun push(pcm: ShortArray, rms: Float, enabled: Boolean): Result {
        frame++
        totalFrames++
        floor = if (rms < floor) max(rms, 0.00005f) else min(floor * FLOOR_CREEP, MAX_FLOOR)
        if (rms > max(floor * 3f, MIN_LEVEL)) lastLoudAt = frame
        val wasActive = active
        active = !enabled || (lastLoudAt >= 0 && frame - lastLoudAt <= HOLD_FRAMES)

        val result = when {
            active && !wasActive -> {
                val out = ArrayList<ShortArray>(ringCount + 1)
                for (i in 0 until ringCount) {
                    out += ring[(ringHead - ringCount + i + preRollFrames) % preRollFrames].copyOf()
                }
                out += pcm
                Result(out, wokeUp = true)
            }
            active -> Result(listOf(pcm), wokeUp = false)
            else -> Result(emptyList(), wokeUp = false)
        }
        processedFrames += result.frames.size

        System.arraycopy(pcm, 0, ring[ringHead], 0, frameSamples)
        ringHead = (ringHead + 1) % preRollFrames
        ringCount = min(ringCount + 1, preRollFrames)
        return result
    }
}
