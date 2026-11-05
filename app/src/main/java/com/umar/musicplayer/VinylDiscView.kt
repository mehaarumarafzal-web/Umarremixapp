package com.umar.musicplayer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class VinylDiscView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null, d: Int = 0) : View(ctx, attrs, d) {
    private var round: Bitmap? = null
    private var playing = false
    private var angle = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 4200; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        addUpdateListener { angle = it.animatedValue as Float; invalidate() }
    }
    private fun dp(v: Float) = v * resources.displayMetrics.density

    fun setPlaying(v: Boolean) {
        playing = v
        if (v) { if (animator.isStarted) animator.resume() else animator.start() } else { if (animator.isStarted) animator.pause() }
        invalidate()
    }

    fun setArt(b: Bitmap?) {
        round = b?.let { makeRound(it) }
        invalidate()
    }

    private fun makeRound(src: Bitmap): Bitmap? = try {
        val size = 512
        val sq = if (src.width == size && src.height == size) src else Bitmap.createScaledBitmap(src, size, size, true)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = BitmapShader(sq, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        out
    } catch (_: Exception) { null }

    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) / 2f - dp(10f)
        paint.shader = null
        paint.style = Paint.Style.FILL; paint.color = 0xFF121212.toInt(); canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1f); paint.color = 0x22FFFFFF
        var gr = r * 0.95f; while (gr > r * 0.63f) { canvas.drawCircle(cx, cy, gr, paint); gr -= dp(6f) }
        val lr = r * 0.58f
        canvas.save(); canvas.rotate(angle, cx, cy)
        val rb = round
        if (rb != null) {
            canvas.drawBitmap(rb, cx - lr, cy - lr, paint)
        } else {
            paint.shader = RadialGradient(cx, cy, lr, 0xFF7C4DFF.toInt(), 0xFF448AFF.toInt(), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, lr, paint)
            paint.shader = null
        }
        paint.color = 0xFF121212.toInt(); paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, lr * 0.10f, paint)
        canvas.restore()
    }
}