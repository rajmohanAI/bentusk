package com.bentusk.app.core.video

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Scales one stretch of sound by a gain that may change over time, given by [gainAt] in milliseconds on the
 * timeline. [offsetMs] is where this stretch starts on the timeline, so fades line up across loops and pieces.
 * Works on 16-bit and float PCM, which is what the encoder decodes to.
 */
@OptIn(UnstableApi::class)
class TimedGain(private val offsetMs: Long, private val gainAt: (Long) -> Float) : BaseAudioProcessor() {

    private var frames = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputAudioFormat
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val size = input.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val channels = format.channelCount
        val count = size / format.bytesPerFrame
        val float = format.encoding == C.ENCODING_PCM_FLOAT
        // The gain is worked out once per 10 ms, which is far finer than any fade can be heard.
        val step = (format.sampleRate / 100).coerceAtLeast(1)
        var gain = 1f
        for (i in 0 until count) {
            if (i % step == 0) gain = gainAt(offsetMs + (frames + i) * 1000 / format.sampleRate)
            for (c in 0 until channels) {
                if (float) {
                    output.putFloat(input.getFloat() * gain)
                } else {
                    val v = (input.getShort() * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    output.putShort(v.toShort())
                }
            }
        }
        frames += count
        input.position(input.limit())
        output.flip()
    }

    override fun onReset() {
        frames = 0
    }
}
