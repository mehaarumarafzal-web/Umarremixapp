package com.umar.musicplayer

import android.content.Context
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min

/**
 * Just Music / Just Lyrics — universal audio processor adapter (final).
 * Compile-time par media3 ki kisi audio-class par depend nahi (compile error namumkin).
 * Runtime par media3 ka AudioProcessor interface dhoond kar proxy se judta hai.
 * mode: 0 = normal, 1 = just music (vocal hataya), 2 = just lyrics (sirf awaaz)
 */
object VoiceFx {

    @Volatile var mode: Int = 0

    fun load(ctx: Context) {
        Store.init(ctx)
        mode = Store.voiceMode()
    }

    fun set(ctx: Context, m: Int) {
        Store.init(ctx)
        Store.setVoiceMode(m)
        mode = m
    }

    /** DefaultRenderersFactory par audio processor jodta hai. Na mil sake to chupchaap false. */
    fun attachTo(factory: Any): Boolean = runCatching {
        val iface = findInterface() ?: return false
        val proxy = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), Handler())
        val arr = java.lang.reflect.Array.newInstance(iface, 1)
        java.lang.reflect.Array.set(arr, 0, proxy)
        val m = factory.javaClass.methods.firstOrNull { it.name == "setAudioProcessors" } ?: return false
        m.invoke(factory, arr)
        true
    }.getOrDefault(false)

    private fun findInterface(): Class<*>? = listOf(
        "androidx.media3.exoplayer.audio.AudioProcessor",
        "androidx.media3.common.AudioProcessor",
        "androidx.media3.decoder.AudioProcessor"
    ).firstNotNullOfOrNull { runCatching { Class.forName(it) }.getOrNull() }

    private const val PCM_16BIT = 2 // C.ENCODING_PCM_16BIT

    private class Handler : InvocationHandler {
        private var out: ByteBuffer = ByteBuffer.allocate(0)
        private var stereo16 = false
        private var eos = false
        private var lastFmt: Any? = null

        override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
            val a = args ?: emptyArray()
            return when (method.name) {
                "configure", "onConfigure" -> {
                    val fmt = a.getOrNull(0)
                    lastFmt = fmt
                    val ch = readInt(fmt, "channelCount")
                    val enc = readInt(fmt, "pcmEncoding").let { if (it == -1) readInt(fmt, "encoding") else it }
                    stereo16 = ch == 2 && (enc == -1 || enc == PCM_16BIT)
                    out = ByteBuffer.allocate(0)
                    eos = false
                    fmt
                }
                "isActive" -> true
                "queueInput" -> {
                    val input = a[0] as ByteBuffer
                    val remaining = input.remaining()
                    val buf = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
                    if (!stereo16 || mode == 0) {
                        buf.put(input)
                    } else {
                        while (input.remaining() >= 4) {
                            val l = input.short.toInt()
                            val r = input.short.toInt()
                            // center (vocal) ka andaza: jab dono channels same disha mein hon
                            val c = if ((l > 0) == (r > 0)) min(abs(l), abs(r)) else 0
                            val v = when (mode) {
                                1 -> ((l - c) + (r - c)) / 2   // vocal kata hua (instrumental)
                                2 -> c                          // sirf vocal
                                else -> (l + r) / 2
                            }
                            val s = v.coerceIn(-32768, 32767).toShort()
                            buf.putShort(s)
                            buf.putShort(s)
                        }
                        input.position(input.limit())
                    }
                    buf.flip()
                    out = buf
                    null
                }
                "queueEndOfStream" -> { eos = true; null }
                "getOutput" -> { val b = out; out = ByteBuffer.allocate(0); b }
                "isEnded" -> eos && out.remaining() == 0
                "flush" -> { out = ByteBuffer.allocate(0); eos = false; null }
                "reset" -> { out = ByteBuffer.allocate(0); eos = false; null }
                "toString" -> "VoiceProcessorProxy"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === a.getOrNull(0)
                else -> {
                    if (lastFmt != null && method.returnType.isInstance(lastFmt)) lastFmt
                    else when (method.returnType) {
                        java.lang.Boolean.TYPE -> false
                        java.lang.Integer.TYPE -> 0
                        java.lang.Long.TYPE -> 0L
                        else -> null
                    }
                }
            }
        }

        private fun readInt(o: Any?, name: String): Int = try {
            val v = o?.javaClass?.getField(name)?.get(o)
            if (v is Int) v else -1
        } catch (_: Exception) { -1 }
    }
}