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
 * Just Music / Just Lyrics — universal audio processor (FINAL v2).
 * Attach ke 2 strategy (array / list) + fail hone par exact reason trace.
 * mode: 0 = normal, 1 = just music (vocal hataya), 2 = just lyrics (sirf awaaz)
 */
object VoiceFx {

    @Volatile var mode: Int = 0
    @Volatile var attached: Boolean = false
        private set
    @Volatile var trace: String = "-"
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
            android.widget.Toast.makeText(
                ctx,
                "Attach fail: $trace",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Factory par processor jodo — array ya list, jo bhi version ho. */
    fun attachTo(factory: Any): Boolean {
        trace = "start"
        try {
            val ms = factory.javaClass.methods.filter { it.name == "setAudioProcessors" }
            if (ms.isEmpty()) { trace = "no-method"; return false }
            for (m in ms) {
                if (m.parameterTypes.size != 1) continue
                val pt = m.parameterTypes[0]

                // Strategy 1: setAudioProcessors(AudioProcessor...)
                if (pt.isArray && pt.componentType.isInterface) {
                    val iface = pt.componentType
                    val proxy = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), Handler())
                    val arr = java.lang.reflect.Array.newInstance(iface, 1)
                    java.lang.reflect.Array.set(arr, 0, proxy)
                    m.invoke(factory, arr)
                    attached = true
                    trace = "ok-array:" + iface.simpleName
                    return true
                }

                // Strategy 2: setAudioProcessors(List<AudioProcessor>)
                if (java.util.List::class.java.isAssignableFrom(pt)) {
                    val comp = guessComponent(factory) ?: continue
                    val proxy = Proxy.newProxyInstance(comp.classLoader, arrayOf(comp), Handler())
                    val list = java.util.ArrayList<Any>()
                    list.add(proxy)
                    m.invoke(factory, list)
                    attached = true
                    trace = "ok-list:" + comp.simpleName
                    return true
                }
            }
            trace = "no-match:" + ms.joinToString("; ") { it.parameterTypes.contentToString() }
            return false
        } catch (t: Throwable) {
            trace = "err:" + (t.message ?: t.javaClass.simpleName)
            return false
        }
    }

    /** List<AudioProcessor> ke andar ka interface generic signature se nikalta hai. */
    private fun guessComponent(factory: Any): Class<*>? {
        for (m in factory.javaClass.methods) {
            if (m.name != "setAudioProcessors") continue
            val gt = m.genericParameterTypes.firstOrNull() as? java.lang.reflect.ParameterizedType ?: continue
            val ta = gt.actualTypeArguments.firstOrNull()
            if (ta is Class<*> && ta.isInterface) return ta
        }
        return null
    }

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