package ai.pocket.heyclaude.wake

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * An additional openWakeWord classifier (e.g. the "Danke Claude" stop word) that scores the
 * embeddings the wake-word pipeline has already computed, so a second word costs only this
 * tiny model instead of another melspectrogram + embedding chain.
 */
class OnnxClassifier(private val env: OrtEnvironment, model: ByteArray) : AutoCloseable {
    private val session: OrtSession
    private val inputName: String
    val embeddingFrames: Int
    private val shape: LongArray
    private val input: FloatBuffer

    init {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(1)
            setInterOpNumThreads(1)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            addConfigEntry("session.intra_op.allow_spinning", "0")
        }
        try {
            session = env.createSession(model, opts)
        } finally {
            opts.close()
        }
        inputName = session.inputNames.first()
        val info = session.inputInfo.getValue(inputName).info as? TensorInfo
        val s = info?.shape ?: longArrayOf(1, -1, FeatureBuffer.EMBEDDING_DIM.toLong())
        require(s.size == 3) { "classifier input must be [1,n,96], is ${s.contentToString()}" }
        embeddingFrames = if (s[1] > 0) s[1].toInt() else FeatureBuffer.DEFAULT_EMBEDDING_FRAMES
        shape = longArrayOf(1, embeddingFrames.toLong(), FeatureBuffer.EMBEDDING_DIM.toLong())
        input = ByteBuffer.allocateDirect(embeddingFrames * FeatureBuffer.EMBEDDING_DIM * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
    }

    /** [features] holds [available] embeddings (oldest first); the newest [embeddingFrames] are used. */
    fun score(features: FloatArray, available: Int): Float {
        require(available >= embeddingFrames) { "classifier needs $embeddingFrames embeddings, pipeline keeps $available" }
        val offset = (available - embeddingFrames) * FeatureBuffer.EMBEDDING_DIM
        input.clear()
        input.put(features, offset, embeddingFrames * FeatureBuffer.EMBEDDING_DIM)
        input.flip()
        OnnxTensor.createTensor(env, input, shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                return (result.get(0) as OnnxTensor).floatBuffer.get(0)
            }
        }
    }

    override fun close() {
        runCatching { session.close() }
    }
}
