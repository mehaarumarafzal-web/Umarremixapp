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
 * Just Music / Just Lyrics — universal audio processor (FINAL).
 * Interface ka naam guess nahi hota: setAudioProcessors(...) ke parameter type
 * se khud mil jata hai, is liye attach kabhi fail nahi hota.
 * mode: 0 = normal, 1 = just music (vocal hataya), 2 = just lyrics (sirf awaaz)
 */
object VoiceFx {

    @Volatile var mode: Int = 0
    @Volatile var attached: Boolean = false
        private set

    fun load(ctx: Context) {
        Store.init(ctx)
        mode = Store.voiceMode()
    }

    fun set(ctx: Context, m: Int) {
        Store.init(ctx)
        Store.setVoiceMode(m)
        mode = m
        if (!attached) {
            android.widget.Toast.makeText(ctx, "Voice processor attach nahi hua - is device par mode apply nahi hoga", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** Factory ke setAudioProcessors ke parameter type se interface lekar proxy jodta hai. */
    fun attachTo(factory: Any): Boolean = runCatching {
        val m = factory.javaClass.methods.firstOrNull { it.name == "setAudioProcessors" && it.parameterTypes.size == 1 }
            ?: return false
        val paramType = m.parameterTypes[0]
        val iface = if (paramType.isArray) paramType.componentType else paramType
        if (iface == null || !iface.isInterface) return false
        val proxy = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), Handler())
        val arr = java.lang.reflect.Array.newInstance(iface, 1)
        java.lang.reflect.Array.set(arr, 0, proxy)
        m.invoke(factory, arr)
        attached = true
        true
    }.getOrDefault(false)

    private const val PCM_16BIT = 2 // C.ENCODING_PCM_16BIT

    private class Handler : InvocationHandler {
        private var out: ByteBuffer = ByteBuffer.allocate(0)
        private var stereo16 = false
        private var eos = false
        private var lastFmt: Any? = null

        override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
            val a = args ?: emptyArray()
            return when (method.name) {
                "configure", "onConfigure" -> { applyConfigure(a.getOrNull(0)); a.getOrNull(0) }
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
                            val c = if ((l > 0) == (r > 0)) min(abs(l), abs(r)) else 0
                            val v = when (mode) {
                                1 -> ((l - c) + (r - c)) / 2
                                2 -> c
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
                    // koi bhi configure-jaisi method (naam jo bhi ho) pakdo
                    if (a.size == 1 && a[0] != null && method.returnType.isInstance(a[0])) {
                        applyConfigure(a[0]); a[0]
                    } else if (lastFmt != null && method.returnType.isInstance(lastFmt)) {
                        lastFmt
                    } else when (method.returnType) {
                        java.lang.Boolean.TYPE -> false
                        java.lang.Integer.TYPE -> 0
                        java.lang.Long.TYPE -> 0L
                        else -> null
                    }
                }
            }
        }

        private fun applyConfigure(fmt: Any?) {
            lastFmt = fmt
            val ch = readInt(fmt, "channelCount")
            var enc = readInt(fmt, "pcmEncoding")
            if (enc == -1) enc = readInt(fmt, "encoding")
            stereo16 = ch == 2 && (enc == -1 || enc == PCM_16BIT)
            out = ByteBuffer.allocate(0)
            eos = false
        }

        private fun readInt(o: Any?, name: String): Int {
            if (o == null) return -1
            try {
                val v = o.javaClass.getField(name).get(o)
                if (v is Int) return v
            } catch (_: Exception) {}
            try {
                val getter = "get" + name.replaceFirstChar { it.uppercase() }
                val v = o.javaClass.getMethod(getter).invoke(o)
                if (v is Int) return v
            } catch (_: Exception) {}
            return -1
        }
    }
}