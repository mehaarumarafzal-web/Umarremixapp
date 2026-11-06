package com.umar.musicplayer

import android.content.Context
import androidx.media3.common.AudioFormat
import androidx.media3.decoder.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.min

/**
 * mode 0 = normal, 1 = just music (vocal hataya), 2 = just lyrics (sirf awaaz)
 * Stereo 16-bit par kaam karta hai; mono/other formats par auto-passthrough (safe).
 */
class VoiceProcessor : BaseAudioProcessor() {

    @Volatile var mode: Int = 0
    private var passthrough = false

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        passthrough = inputAudioFormat.encoding != AudioFormat.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2
        return inputAudioFormat
    }

    override fun onQueueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        val out = replaceOutputBuffer(remaining)
        if (passthrough || mode == 0) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        while (inputBuffer.remaining() >= 4) {
            val l = inputBuffer.short.toInt()
            val r = inputBuffer.short.toInt()
            val c = if ((l > 0) == (r > 0)) min(abs(l), abs(r)) else 0
            val v = when (mode) {
                1 -> ((l - c) + (r - c)) / 2
                2 -> c
                else -> (l + r) / 2
            }
            val s = v.coerceIn(-32768, 32767).toShort()
            out.putShort(s)
            out.putShort(s)
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }
}

object VoiceFx {
    val processor = VoiceProcessor()
    fun load(ctx: Context) {
        Store.init(ctx)
        processor.mode = Store.voiceMode()
    }
    fun set(ctx: Context, m: Int) {
        Store.init(ctx)
        Store.setVoiceMode(m)
        processor.mode = m
    }
}